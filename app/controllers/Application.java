package controllers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nu.xom.Document;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.XML;

import play.libs.Json;
import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Result;
import uk.ac.cam.ch.wwmm.chemicaltagger.Utils;
import act.server.Molecules.BRO;
import act.server.Molecules.CRO;
import act.server.Molecules.DotNotation;
import act.server.Molecules.ERO;
import act.server.Molecules.RO;
import act.server.Molecules.ReactionDiff;
import act.server.Molecules.RxnTx;
import act.server.Molecules.SMILES;
import act.server.Molecules.TheoryROs;
import act.server.SQLInterface.MongoDB;
import act.shared.AAMFailException;
import act.shared.MalFormedReactionException;
import act.shared.OperatorInferFailException;
import act.shared.SMARTSCanonicalizationException;
import act.shared.helpers.P;

import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;

public class Application extends Controller {

	public static MongoDB mongoDB = null;
	public static Indigo indigo = null;
	public static IndigoInchi indigoInchi = null;

	public static Result index() {
		String title = "============\nACT REST API\n============";
		String apply = "APPLY RO\n----------------------\nTo apply an RO send GET request to /apply with the following JSON parameters:";
		String applyParams = "{\"substrate\": <your substrate in smiles>, \"ro_type\": <ERO or CRO>, \"rxn_id\": <the representative reaction id of the ro_type>}";
		String applyEx = "Example input:\ncurl --header \"Content-type: application/json\" --request GET --data '{\"rxn_id\":\"1\",\"ro_type\":\"CRO\",\"substrate\":\"[C@@H]12[C@@H](O1)[C@@H](C=C(C2=O)CO)O\"}' http://localhost:9000/apply";
		String applyExOut = "Example output:\n{\"reverse\":[[\"[H]C([H])(O[H])C1=C([H])C([H])(O[H])C2([H])OC2([H])C1([H])O[H]\"]],\"forward\":[[\"OC1C(=C([H])C(=O)C2([H])OC21[H])C([H])([H])O[H]\"],[\"O=C([H])C1C([H])C([H])(O[H])C2([H])OC2([H])C1=O\"]]}";
		String infer = "INFER ERO\n----------------------\nTo infer an ERO send GET request to /infer with the following JSON parameters:";
		String inferParams = "{\"substrates\": [<list of your substrate in smiles>], \"products\": [<list of your products in smiles>]}";
		String inferEx = "Example input:\ncurl --header \"Content-type: application/json\" --request GET --data '{\"substrates\": [\"[C@@H]23[C@H]([C@H]1[C@]([C@@H](C(C)=O)CC1)(C)CC2)CCC4=CC(=O)CC[C@]34C\"], \"products\": [\"[C@H]34[C@H]2[C@@H]([C@@]1(C(=CC(=O)CC1)CC2)C)CC[C@@]3([C@@H](C(CO)=O)CC4)C\"]}' http://localhost:9000/infer";
		String inferExOut = "Example output:\n{\"BRO\":\"{C-O=1, C-H=-1, H-O=1}\",\"CRO\":\"{ [H,*:1]O[H].[H,*:2]C([H,*:3])([H])[H,*:4][Ac]>>[H,*:1]OC([H,*:2])([H,*:3])[H,*:4][Ac] }\",\"ERO\":\"{ [H,*:1]C([H,*:2])([H,*:3])C([Ac])(O[Ac])C([H])([H])[H]>>[H,*:1]C([H,*:2])([H,*:3])C([Ac])(O[Ac])C([H])([H])O[H] }\"}";
		String tag = "TAG CHEMICALS\n----------------------\nBased off of University of Cambridge's ChemicalTagger found here: http://chemicaltagger.ch.cam.ac.uk/index.html.\nTo tag chemicals in text send GET request to /tag with the following JSON paramters:";
		String tagParams = "{\"paper\": <any chunk of text>}";
		String tagEx = "curl --header \"Content-type: application/json\" --request GET --data '{\"paper\": \"Reduction of 5-methoxy-6-formyl(Ia)- and 5-formyl-6-methoxy-2,3-diphenylbenzofuran (IVa) yielded 6- and 5-methyl derivatives Ib and IVb, respectively.\"}' http://localhost:9000/tag";
		String tagExOut = "{\"Document\":{\"Sentence\":{\"Unmatched\":{\"RB\":\"respectively\"},\"STOP\":\".\",\"COMMA\":\",\",\"ActionPhrase\":{\"NounPhrase\":[{\"PrepPhrase\":{\"NounPhrase\":{\"MOLECULE\":[{\"OSCARCM\":{\"OSCAR-CM\":\"5-methoxy-6-formyl(Ia)-\"}},{\"OSCARCM\":[{\"OSCAR-CM\":\"5-formyl-6-methoxy-2,3-diphenylbenzofuran\"},{\"_-RRB-\":\")\",\"OSCAR-CM\":\"IVa\",\"_-LRB-\":\"(\"}]}],\"CC\":\"and\"},\"IN-OF\":\"of\"},\"NN\":\"Reduction\"},{\"NNS\":\"derivatives\",\"MOLECULE\":[{\"OSCARCM\":{\"OSCAR-CM\":\"5-methyl\"}},{\"OSCARCM\":{\"OSCAR-CM\":\"Ib\"}},{\"OSCARCM\":{\"OSCAR-CM\":\"IVb\"}}],\"CC\":[\"and\",\"and\"],\"CD\":\"6-\"}],\"type\":\"Yield\",\"VerbPhrase\":{\"VB-YIELD\":\"yielded\"}}}}}";
		return ok(title + "\n\n\n" + apply + "\n" + applyParams + "\n\n"
				+ applyEx + "\n\n" + applyExOut + "\n\n\n" + infer + "\n"
				+ inferParams + "\n\n" + inferEx + "\n\n" + inferExOut
				+ "\n\n\n" + tag + "\n" + tagParams + "\n\n" + tagEx + "\n\n"
				+ tagExOut);
	}

	// This function will be used by all server side function to initiate
	// connection to the backend DB
	private static MongoDB createActConnection(String mongoActHost,
			int mongoActPort, String mongoActDB) {
		MongoDB db;
		// connect to Mongo database
		if (mongoActHost == null) {
			// this means that right now we are in simulation mode and only want
			// to write to screen
			// as opposed to write to the actual database. So we
			// System.out.println everything
			db = null;
		} else {
			db = new MongoDB(mongoActHost, mongoActPort, mongoActDB);
		}
		return db;
	}

	@BodyParser.Of(BodyParser.Json.class)
	public static Result infer() {
		JsonNode json = request().body().asJson();
		if (json == null) {
			return badRequest("missing json in request");
		} else if (json.findValue("substrates") == null) {
			return badRequest("missing substrates in request");
		} else if (json.findValue("products") == null) {
			return badRequest("missing products in request");
		}
		ObjectMapper mapper = new ObjectMapper();
		List<String> substrates = null;
		List<String> products = null;
		try {
			substrates = mapper.readValue(json.findValue("substrates"),
					new TypeReference<List<String>>() {
					});
			products = mapper.readValue(json.findValue("products"),
					new TypeReference<List<String>>() {
					});
		} catch (JsonParseException e) {
			e.printStackTrace();
		} catch (JsonMappingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (substrates == null) {
			return badRequest("error parsing substrates");
		} else if (products == null) {
			return badRequest("error parsing products");
		}
		System.out.println(substrates);
		System.out.println(products);
		BRO broFull = SMILES.computeBondRO(substrates, products);
		int id = -1; // is this argument used at all?
		P<List<String>, List<String>> reaction = null;
		TheoryROs theoryRO = null;
		try {
			reaction = ReactionDiff.balanceTheReducedReaction(id, substrates,
					products);
			theoryRO = SMILES.ToReactionTransform(id, reaction, broFull);
		} catch (AAMFailException e) {
			e.printStackTrace();
		} catch (MalFormedReactionException e) {
			e.printStackTrace();
		} catch (OperatorInferFailException e) {
			e.printStackTrace();
		} catch (SMARTSCanonicalizationException e) {
			e.printStackTrace();
		}
		Map<String, String> result = new HashMap<String, String>();
		result.put("ERO", theoryRO.ERO().toString());
		result.put("CRO", theoryRO.CRO().toString());
		result.put("BRO", theoryRO.BRO().toString());
		return ok(Json.toJson(result));
	}

	@BodyParser.Of(BodyParser.Json.class)
	public static Result tag() {
		JsonNode json = request().body().asJson();
		System.out.println(json);
		String abstractText = json.findPath("paper").getTextValue();
		if (abstractText == null) {
			return badRequest("Missing parameter [paper]");
		}
		String xmltag = null;
		JSONObject item = null;
		try {
			Document doc = Utils.runChemicalTagger(abstractText);
			xmltag = doc.toXML();
			item = XML.toJSONObject(xmltag);
			return ok(Json.parse(item.toString()));
		} catch (JSONException e) {
			return badRequest("Error parsing " + e);
		} catch (Exception e1) {
			return badRequest("Error parsing " + e1);
		}
	}

	@BodyParser.Of(BodyParser.Json.class)
	public static Result apply() {
		JsonNode json = request().body().asJson();
		if (json == null) {
			return badRequest("missing json in request");
		}
		JsonNode sub = json.findValue("substrate");
		JsonNode rxn = json.findValue("rxn_id");
		JsonNode ro = json.findValue("ro_type");
		if (sub == null || rxn == null || ro == null) {
			return badRequest("Request must have json dict with keys substrate, rxn_id, ro_type.");
		}
		String substrate = sub.getTextValue();
		long rxn_id = rxn.asLong();
		String ro_type = ro.getTextValue();

		HashMap<String, List<List<String>>> products = getProducts(substrate,
				rxn_id, ro_type);
		JSONObject resp = new JSONObject(products);
		return ok(Json.parse(resp.toString()));
	}

	public static List<List<String>> applyRO(List<String> substrates, RO ro,
			boolean is_smiles) {
		List<List<String>> rxnProducts = null;
		rxnProducts = RxnTx.expandChemical2AllProducts(substrates, ro, indigo,
				indigoInchi);
		List<List<String>> products = new ArrayList<List<String>>();
		if (rxnProducts == null) {
			products.add(new ArrayList<String>());
			return products;
		} else {
			for (List<String> prods : rxnProducts) {
				List<String> rxn_prods = new ArrayList<String>();
				for (String p : prods) {
					IndigoObject prod = indigo.loadMolecule(p);
					String prodSMILES = DotNotation.ToNormalMol(prod, indigo);
					rxn_prods.add(prodSMILES);
				}
				products.add(rxn_prods);
			}
		}
		return products;
	}

	private static String toDotNotation(String substrateSMILES, Indigo indigo) {
		IndigoObject mol = indigo.loadMolecule(substrateSMILES);
		mol = DotNotation.ToDotNotationMol(mol);
		return mol.canonicalSmiles(); // do not necessarily need the
										// canonicalSMILES
	}

	public static HashMap<String, List<List<String>>> getProducts(
			String substrate, long roRep, String roType) {
		if (mongoDB == null) {
			mongoDB = createActConnection("pathway.berkeley.edu", 27017,
					"actv01");
			System.out.println("Created new MongoDB connection");
		}
		if (indigo == null) {
			indigo = new Indigo();
			System.out.println("Created new Indigo");
		}
		if (indigoInchi == null) {
			indigoInchi = new IndigoInchi(indigo);
			System.out.println("Created new IndigoInchi");
		}
		boolean is_smiles = true;
		List<String> substrates = new ArrayList<String>();
		// in SMILES, we can just split on "."
		for (String s : substrate.split("[.]")) {
			substrates.add(toDotNotation(s, indigo));
		}

		HashMap<String, List<List<String>>> ros = new HashMap<String, List<List<String>>>();
		// roType is one of BRO, CRO, ERO, OP to pull from appropriate DB.
		RO ro = mongoDB.getROForRxnID(roRep, roType, true);
		RO ro_reverse = null;
		if (ro instanceof CRO) {
			CRO cro = (CRO) ro;
			ro_reverse = cro.reverse();
		} else if (ro instanceof ERO) {
			ERO ero = (ERO) ro;
			ro_reverse = ero.reverse();
		}
		ros.put("forward", applyRO(substrates, ro, is_smiles));
		ros.put("reverse", applyRO(substrates, ro_reverse, is_smiles));
		return ros;
	}
}

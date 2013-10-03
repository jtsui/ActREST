package controllers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.json.JSONObject;

import play.libs.Json;
import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Result;
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
		String title = "Act REST API";
		String apply = "To apply an RO send GET request to /apply with the following JSON parameters:";
		String applyParams = "{\"substrate\": <your substrate in smiles>, \"ro_type\": <ERO or CRO>, \"rxn_id\": <the representative reaction id of the ro_type>}";
		String applyEx = "Example: curl --header \"Content-type: application/json\" --request GET --data '{\"rxn_id\":\"1\",\"ro_type\":\"CRO\",\"substrate\":\"[C@@H]12[C@@H](O1)[C@@H](C=C(C2=O)CO)O\"}' http://localhost:9000/apply";
		String infer = "To infer an ERO send GET request to /infer with the following JSON parameters:";
		String inferParams = "{\"substrates\": [<list of your substrate in smiles>], \"products\": [<list of your products in smiles>]}";
		return ok(title + "\n\n" + apply + "\n" + applyParams + "\n" + applyEx
				+ "\n\n" + infer + "\n" + inferParams);
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

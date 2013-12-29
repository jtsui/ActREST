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

import play.libs.Json;
import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Result;
import uk.ac.cam.ch.wwmm.chemicaltagger.Utils;
import act.server.ActAdminServiceImpl;
import act.server.Logger;
import act.server.Molecules.BRO;
import act.server.Molecules.DotNotation;
import act.server.Molecules.ERO;
import act.server.Molecules.RO;
import act.server.Molecules.ReactionDiff;
import act.server.Molecules.SMILES;
import act.server.Molecules.TheoryROs;
import act.server.SQLInterface.MongoDB;
import act.shared.AAMFailException;
import act.shared.MalFormedReactionException;
import act.shared.OperatorInferFailException;
import act.shared.SMARTSCanonicalizationException;
import act.shared.helpers.P;

import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoException;
import com.ggasoftware.indigo.IndigoObject;

public class Application extends Controller {

	public static MongoDB mongoDB = null;
	public static Indigo indigo = null;

	public static Result index() {
		String title = "============\nACT REST API\n============";
		String apply = "APPLY RO\n----------------------\nTo apply an RO send GET request to /apply with the following JSON parameters:";
		String applyParams = "{\"substrates\": [<list of substrates in smiles>], \"ero_id\": <id of ero to apply>}";
		String applyEx = "Example input:\ncurl --header \"Content-type: application/json\" --request GET --data '{\"ero_id\":\"84740171\",\"substrates\":[\"CC12CCC(=O)CC1CCC3C2CCC4(C3CCC4C(=O)COC(=O)CCC(=O)O)C\"]}' http://pathway.berkeley.edu:27329/apply";
		String applyExOut = "Example output:\n[[\"OC(C(O)OC(O)C([H])([H])C([H])([H])C(=O)O[H])C1([H])C2(C([H])(C3([H])C([H])(C4(C([H])(C([H])([H])C(=O)C([H])([H])C4([H])[H])C([H])([H])C3([H])[H])C([H])([H])[H])C([H])([H])C2([H])[H])C([H])([H])C1([H])[H])C([H])([H])[H]\"]]";
		String infer = "INFER ERO\n----------------------\nTo infer an ERO send GET request to /infer with the following JSON parameters:";
		String inferParams = "{\"substrates\": [<list of your substrate in smiles>], \"products\": [<list of your products in smiles>]}";
		String inferEx = "Example input:\ncurl --header \"Content-type: application/json\" --request GET --data '{\"substrates\": [\"[C@@H]23[C@H]([C@H]1[C@]([C@@H](C(C)=O)CC1)(C)CC2)CCC4=CC(=O)CC[C@]34C\"], \"products\": [\"[C@H]34[C@H]2[C@@H]([C@@]1(C(=CC(=O)CC1)CC2)C)CC[C@@]3([C@@H](C(CO)=O)CC4)C\"]}' http://pathway.berkeley.edu:27329/infer";
		String inferExOut = "Example output:\n{\"BRO\":\"{C-O=1, C-H=-1, H-O=1}\",\"CRO\":\"{ [H,*:1]O[H].[H,*:2]C([H,*:3])([H])[H,*:4][Ac]>>[H,*:1]OC([H,*:2])([H,*:3])[H,*:4][Ac] }\",\"ERO\":\"{ [H,*:1]C([H,*:2])([H,*:3])C([Ac])(O[Ac])C([H])([H])[H]>>[H,*:1]C([H,*:2])([H,*:3])C([Ac])(O[Ac])C([H])([H])O[H] }\"}";
		String tag = "TAG CHEMICALS\n----------------------\nBased off of University of Cambridge's ChemicalTagger found here: http://chemicaltagger.ch.cam.ac.uk/index.html.\nTo tag chemicals in text send GET request to /tag with the following JSON paramters:";
		String tagParams = "{\"paper\": <any chunk of text>}";
		String tagEx = "Example input:\ncurl --header \"Content-type: application/json\" --request GET --data '{\"paper\": \"Reduction of 5-methoxy-6-formyl(Ia)- and 5-formyl-6-methoxy-2,3-diphenylbenzofuran (IVa) yielded 6- and 5-methyl derivatives Ib and IVb, respectively.\"}' http://pathway.berkeley.edu:27329/tag";
		String tagExOut = "Example output:\n{\"Document\":{\"Sentence\":{\"Unmatched\":{\"RB\":\"respectively\"},\"STOP\":\".\",\"COMMA\":\",\",\"ActionPhrase\":{\"NounPhrase\":[{\"PrepPhrase\":{\"NounPhrase\":{\"MOLECULE\":[{\"OSCARCM\":{\"OSCAR-CM\":\"5-methoxy-6-formyl(Ia)-\"}},{\"OSCARCM\":[{\"OSCAR-CM\":\"5-formyl-6-methoxy-2,3-diphenylbenzofuran\"},{\"_-RRB-\":\")\",\"OSCAR-CM\":\"IVa\",\"_-LRB-\":\"(\"}]}],\"CC\":\"and\"},\"IN-OF\":\"of\"},\"NN\":\"Reduction\"},{\"NNS\":\"derivatives\",\"MOLECULE\":[{\"OSCARCM\":{\"OSCAR-CM\":\"5-methyl\"}},{\"OSCARCM\":{\"OSCAR-CM\":\"Ib\"}},{\"OSCARCM\":{\"OSCAR-CM\":\"IVb\"}}],\"CC\":[\"and\",\"and\"],\"CD\":\"6-\"}],\"type\":\"Yield\",\"VerbPhrase\":{\"VB-YIELD\":\"yielded\"}}}}}";
		return ok(title + "\n\n\n" + apply + "\n" + applyParams + "\n\n"
				+ applyEx + "\n\n" + applyExOut + "\n\n\n" + infer + "\n"
				+ inferParams + "\n\n" + inferEx + "\n\n" + inferExOut
				+ "\n\n\n" + tag + "\n" + tagParams + "\n\n" + tagEx + "\n\n"
				+ tagExOut);
	}

	@BodyParser.Of(BodyParser.Json.class)
	public static Result infer() {
		Logger.setMaxImpToShow(-1); // don't show any output
		System.err.close();
		JsonNode json = request().body().asJson();
		Map<String, String> result = new HashMap<String, String>();
		result.put("error", "");
		if (json == null) {
			result.put("error", "missing json in request");
			return badRequest(Json.toJson(result));
		} else if (json.findValue("substrates") == null) {
			result.put("error", "missing substrates in request");
			return badRequest(Json.toJson(result));
		} else if (json.findValue("products") == null) {
			result.put("error", "missing products in request");
			return badRequest(Json.toJson(result));
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
			result.put("error", "error parsing json");
			return badRequest(Json.toJson(result));
		} catch (JsonMappingException e) {
			result.put("error", "error parsing json");
			return badRequest(Json.toJson(result));
		} catch (IOException e) {
			result.put("error", "error parsing json");
			return badRequest(Json.toJson(result));
		}
		if (substrates == null) {
			result.put("error", "error parsing substrates");
			return badRequest(Json.toJson(result));
		} else if (products == null) {
			result.put("error", "error parsing products");
			return badRequest(Json.toJson(result));
		}
		int id = -1; // is this argument used at all?
		P<List<String>, List<String>> reaction = null;
		TheoryROs theoryRO = null;
		try {
			BRO broFull = SMILES.computeBondRO(substrates, products);
			reaction = ReactionDiff.balanceTheReducedReaction(id, substrates,
					products);
			theoryRO = SMILES.ToReactionTransform(id, reaction, broFull);
		} catch (AAMFailException e) {
			result.put("error", "AAMFailException");
			return badRequest(Json.toJson(result));
		} catch (MalFormedReactionException e) {
			result.put("error", "MalFormedReactionException");
			return badRequest(Json.toJson(result));
		} catch (OperatorInferFailException e) {
			result.put("error", "OperatorInferFailException");
			return badRequest(Json.toJson(result));
		} catch (SMARTSCanonicalizationException e) {
			result.put("error", "SMARTSCanonicalizationException");
			return badRequest(Json.toJson(result));
		} catch (IndigoException e) {
			result.put("error", "IndigoException");
			return badRequest(Json.toJson(result));
		} catch (Exception e) {
			result.put("error", "other exception" + e);
			return badRequest(Json.toJson(result));
		}
		result.put("ERO", theoryRO.ERO().toString());
		result.put("CRO", theoryRO.CRO().toString());
		result.put("BRO", theoryRO.BRO().toString());
		return ok(Json.toJson(result));
	}

	@BodyParser.Of(BodyParser.Json.class)
	public static Result tag() {
		// Logger.setMaxImpToShow(-1); // don't show any output
		// System.err.close();
		JsonNode json = request().body().asJson();
		Map<String, String> result = new HashMap<String, String>();
		result.put("error", "");
		if (json == null) {
			result.put("error", "missing json in request");
			return badRequest(Json.toJson(result));
		}
		String abstractText = json.findPath("paper").getTextValue();
		if (abstractText == null) {
			result.put("error", "missing paper");
			return badRequest(Json.toJson(result));
		}
		String xmltag = null;
		try {
			Document doc = Utils.runChemicalTagger(abstractText);
			xmltag = doc.toXML();
			return ok(xmltag);
		} catch (Exception e) {
			result.put("error", "Exception: " + e);
			return badRequest(Json.toJson(result));
		}
	}

	private static ERO getERO(int ero_id) {
		if (mongoDB == null) {
			mongoDB = new MongoDB("pathway.berkeley.edu", 30000, "actv01");
		}
		P<ERO, Integer> ero = mongoDB.getERO(ero_id);
		return ero.fst();
	}

	private static Indigo getIndigo() {
		if (indigo == null) {
			indigo = new Indigo();
		}
		return indigo;
	}

	public static List<List<String>> resultDotToSMILES(List<List<String>> result) {
		List<List<String>> output = new ArrayList<List<String>>();
		if (result == null) {
			return output;
		}
		for (List<String> products : result) {
			List<String> pout = new ArrayList<String>();
			for (String p : products) {
				IndigoObject prod = indigo.loadMolecule(p);
				String prodSMILES = DotNotation.ToNormalMol(prod, indigo);
				pout.add(prodSMILES);
			}
			output.add(pout);
		}
		return output;
	}

	public static List<String> smilesToDot(List<String> chemicals) {
		List<String> chemicalsDotNotation = new ArrayList<String>();
		for (String chemical : chemicals) {
			chemicalsDotNotation.add(ActAdminServiceImpl.toDotNotation(
					chemical, getIndigo()));
		}
		return chemicalsDotNotation;
	}

	@BodyParser.Of(BodyParser.Json.class)
	public static Result apply() {
		Logger.setMaxImpToShow(-1); // don't show any output
		System.err.close();
		JsonNode json = request().body().asJson();
		Map<String, String> result = new HashMap<String, String>();
		result.put("error", "");
		if (json == null) {
			result.put("error", "missing json in request");
			return badRequest(Json.toJson(result));
		} else if (json.findValue("substrates") == null) {
			result.put("error", "missing substrates in request");
			return badRequest(Json.toJson(result));
		} else if (json.findValue("ero_id") == null) {
			result.put("error", "missing ero id in request");
			return badRequest(Json.toJson(result));
		}
		ObjectMapper mapper = new ObjectMapper();
		List<String> substrates = null;
		try {
			substrates = mapper.readValue(json.findValue("substrates"),
					new TypeReference<List<String>>() {
					});
		} catch (JsonParseException e) {
			result.put("error", "error parsing json");
			return badRequest(Json.toJson(result));
		} catch (JsonMappingException e) {
			result.put("error", "error parsing json");
			return badRequest(Json.toJson(result));
		} catch (IOException e) {
			result.put("error", "error parsing json");
			return badRequest(Json.toJson(result));
		}
		int ero_id = json.findValue("ero_id").asInt();
		RO ero = getERO(ero_id);
		if (ero == null) {
			result.put("error", "could not find ero with ero_id " + ero_id);
			return badRequest(Json.toJson(result));
		}
		List<String> substratesDotNotation = smilesToDot(substrates);
		List<List<String>> output = ActAdminServiceImpl
				.applyRO_MultipleSubstrates_DOTNotation(substratesDotNotation,
						ero);
		return ok(Json.toJson(resultDotToSMILES(output)));
	}
}

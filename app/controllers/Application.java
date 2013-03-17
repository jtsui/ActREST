package controllers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.codehaus.jackson.JsonNode;
import org.json.JSONArray;

import play.libs.Json;
import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Result;
import views.html.index;
import act.server.EnumPath.Enumerator;
import act.server.Molecules.RO;
import act.server.SQLInterface.MongoDB;

import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;

public class Application extends Controller {

	public static Result index() {
		return ok(index.render("Your new application is ready."));
	}

	@BodyParser.Of(BodyParser.Json.class)
	public static Result apply() {
		JsonNode json = request().body().asJson();
		if (json == null) {
			return badRequest("missing json in request");
		}
		String substrate = json.findPath("substrate").getTextValue();
		long rxn_id = json.findPath("rxn_id").asLong();
		String ro_type = json.findPath("ro_type").getTextValue();
		if (substrate == null || rxn_id == 0 || ro_type == null) {
			return badRequest("Request must have json dict with keys substrate, rxn_id, ro_type.");
		}
		List<String> products = applyRO_OnOneSubstrate("pathway.berkeley.edu",
				27017, "actv01", substrate, rxn_id, ro_type);
		if (products == null) {
			return ok("NONE");
		}
		JSONArray json_arr = new JSONArray();
		for (String product : products) {
			json_arr.put(product);
		}
		return ok(Json.parse(json_arr.toString()));
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

	public static List<String> applyRO_OnOneSubstrate(String mongoActHost,
			int mongoActPort, String mongoActDB, String substrate, long roRep,
			String roType) {
		MongoDB mongoDB = createActConnection(mongoActHost, mongoActPort,
				mongoActDB);
		Indigo indigo = new Indigo();
		IndigoInchi indigoInchi = new IndigoInchi(indigo);

		if (!substrate.startsWith("InChI=")) {
			IndigoObject mol = indigo.loadMolecule(substrate);
			substrate = indigoInchi.getInchi(mol);
		}
		Set<String> substrates = new HashSet<String>();
		substrates.add(substrate);

		// roType is one of BRO, CRO, ERO, OP to pull from appropriate DB.
		RO ro = mongoDB.getROForRxnID(roRep, roType, true);
		List<List<String>> rxnProducts = Enumerator
				.expandChemicalUsingOperatorInchi_AllProducts(substrates, ro,
						indigo, indigoInchi);
		List<String> stringProducts = new ArrayList<String>();
		if (rxnProducts == null) {
			// System.out.println("NONE");
			return null;
		} else {
			for (List<String> products : rxnProducts) {
				for (String p : products) {
					// System.out.println(indigoInchi.loadMolecule(p).smiles());
					stringProducts.add(indigoInchi.loadMolecule(p).smiles()
							.toString());
				}
			}
		}
		return stringProducts;
	}
}

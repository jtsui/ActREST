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
import com.ggasoftware.indigo.IndigoException;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;

public class Application extends Controller {

	public static MongoDB mongoDB = null;
	public static Indigo indigo = null;
	public static IndigoInchi indigoInchi = null;

	public static Result index() {
		return ok(index.render("Your new application is ready."));
	}

	@BodyParser.Of(BodyParser.Json.class)
	public static Result apply() {
		JsonNode json = request().body().asJson();
		if (json == null) {
			return badRequest("missing json in request");
		}
		JsonNode sub = json.findPath("substrate");
		JsonNode rxn = json.findPath("rxn_id");
		JsonNode ro = json.findPath("ro_type");
		if (sub == null || rxn == null || ro == null) {
			return badRequest("Request must have json dict with keys substrate, rxn_id, ro_type.");
		}
		String substrate = sub.getTextValue();
		long rxn_id = rxn.asLong();
		String ro_type = ro.getTextValue();

		List<String> products = null;
		JSONArray json_arr = new JSONArray();
		products = applyRO_OnOneSubstrate(substrate, rxn_id, ro_type);
		if (products == null || products.isEmpty()) {
			return ok(Json.parse(json_arr.toString()));
		}
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

	public static List<String> applyRO_OnOneSubstrate(String substrate,
			long roRep, String roType) {
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
		if (!substrate.startsWith("InChI=")) {
			try {
				IndigoObject mol = indigo.loadMolecule(substrate);
				substrate = indigoInchi.getInchi(mol);
			} catch (IndigoException e) {
				return null;
			}
		}
		Set<String> substrates = new HashSet<String>();
		substrates.add(substrate);
		// roType is one of BRO, CRO, ERO, OP to pull from appropriate DB.
		RO ro = mongoDB.getROForRxnID(roRep, roType, true);
		List<List<String>> rxnProducts = null;
		rxnProducts = Enumerator.expandChemicalUsingOperatorInchi_AllProducts(
				substrates, ro, indigo, indigoInchi);
		List<String> products = new ArrayList<String>();
		if (rxnProducts == null) {
			return null;
		} else {
			for (List<String> prods : rxnProducts) {
				for (String p : prods) {
					products.add(indigoInchi.loadMolecule(p).smiles()
							.toString());
				}
			}
		}
		return products;
	}
}

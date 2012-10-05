/*
 * register.js:
 */

;(function() {

	var keys = ["personId", "firstName", "lastName", "email", "street", "city", "zip", "phone", "password", "confirmPassword", "street", "zip", "city"];
	
	function json(model) {
		var d = {};
		for (var i in keys) {
			var key = keys[i];
			d[key] = model[key]() || null;
		}
		
		delete d.confirmPassword;
		return d;
	}
	
	function reset(model) {
		for (var i in keys) {
			model[keys[i]]("");
			model[keys[i]].isModified(false);
		}
		model["personId"] = null;
		return false;
	}
	
	function submit(m) {
		ajax.command("register-user", json(m))
			.success(function(e) {
				$("#register-email-error").html("&nbsp;");
				var login = model().email();
				var password = model().password();
				reset(model());
				ajax.post("/rest/login")
					.param("username", login)
					.param("password", password)
					.success(function(e) { window.location = "/applicant"; })
					.call();
			})
			.error(function(e) {
				// FIXME: DIRTY HACKS
				if (e.text.indexOf("lupapalvelu.users.$email_1") != -1) {
					$("#register-email-error").html("sahkopostiosoite on jo varattu.");
				}
				if (e.text.indexOf("duplicate key error index: lupapalvelu.users.$personId_1") != -1) {
					$("#register-email-error").html("hetu on jo varattu.");
				}
				error(e.text);
				// TODO: now what?
			})
			.call();
		return false;
	}
	
	//TODO: personId, firstName & lastName should come from Tupas
	var model = {
		personId: ko.observable(Math.floor(Math.random() * 1000000) + "-1234"),
		firstName: ko.observable(["Matti", "Teppo", "Seppo", "Maija", "Kaija", "Raija"][Math.floor(Math.random()*5)]),
		lastName: ko.observable(["Nieminen", "Korhonen", "H\u00E4m\u00E4l\u00E4inen", "Himanen", "Jormanainen", "Ehn"][Math.floor(Math.random()*5)]),
		street: ko.observable().extend({required: true}),
		city: ko.observable().extend({required: true}),
		zip: ko.observable().extend({required: true, number: true, maxLength: 5}),
		phone: ko.observable().extend({required: true}),
		email: ko.observable().extend({email: true}),
		password: ko.observable().extend({minLength: 6}),
		disabled: ko.observable(true),
		submit: submit,
		reset: reset
	};
	
	model.confirmPassword = ko.observable().extend({equal: model.password});
	model = ko.validatedObservable(model);
	model.isValid.subscribe(function(valid) { model().disabled(!valid); });
	
	$(function() {
		ko.applyBindings(model, $("#register")[0]);
		ko.applyBindings(model, $("#register2")[0]);
		$("#test-skip-tupas").click(function() { window.location.hash = "!/register2"; });
	});
	
})();

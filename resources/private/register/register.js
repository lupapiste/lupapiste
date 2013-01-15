;(function() {

  var keys = ["stamp", "personId", "firstname", "lastname", "email", "street", "city", "zip", "phone", "password", "confirmPassword", "street", "zip", "city"];

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
    return false;
  }

  function submit(m) {
    ajax.command("register-user", json(m))
      .success(function(e) {
        $("#register-email-error").html("&nbsp;");
        var login = model().email();
        var password = model().password();
        reset(model());
        ajax.post("/api/login")
          .param("username", login)
          .param("password", password)
          .success(function(e) { window.location = "/applicant"; })
          .call();
      })
      .error(function(e) {
        // FIXME: DIRTY HACKS
        if (e.text.indexOf("lupapalvelu.users.$email_1") !== -1) {
          $("#register-email-error").html("sahkopostiosoite on jo varattu.");
        }
        if (e.text.indexOf("duplicate key error index: lupapalvelu.users.$personId_1") !== -1) {
          $("#register-email-error").html("hetu on jo varattu.");
        }
        error(e.text);
        // TODO: now what?
      })
      .call();
    return false;
  }

  var model = {
    personId: ko.observable(),
    firstname: ko.observable(),
    lastname: ko.observable(),
    stamp: ko.observable(),
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
  model.isValid.subscribe(function(valid) {
    model().disabled(!valid);
  });

  hub.onPageChange("register", function() {
    $.get("/vetuma", {success: "/welcome#!/register2",
                    cancel:  "/welcome#!/register/cancel",
                    error:   "/welcome#!/register/error"},function(d) {
      $("#vetuma-register").html(d).find(":submit").addClass("btn btn-primary")
                        .attr('value','Tunnistaudu')
                        .attr("id", "vetuma-init");
    });
  });

  hub.onPageChange("register2", function() {
    $.get("/vetuma/user", function(data) {
      model().personId(data.userid);
      model().firstname(data.firstname);
      model().lastname(data.lastname);
      model().stamp(data.stamp);
    });

    ko.applyBindings(model, $("#register2")[0]);
  });
})();

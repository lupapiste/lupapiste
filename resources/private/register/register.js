;(function() {
  "use strict";

  var registrationModel = new LUPAPISTE.RegistrationModel();

  var model;
  var confirmModel = {
    email: ko.observable("")
  };

  function submit(m) {
    var error$ = $("#register-email-error");
    error$.text("");

    ajax.command("register-user", registrationModel.json(m))
      .success(function() {
        confirmModel.email(model().email());
        registrationModel.reset(model());
        window.location.hash = "!/register3";
      })
      .error(function(e) {
        error$.text(loc(e.text));
      })
      .call();
    return false;
  }

  function cancel() {
    LUPAPISTE.ModalDialog.showDynamicYesNo(
      loc("areyousure"),
      loc("register.confirm-cancel"),
      {title: loc("yes"),
       fn: function() {
        registrationModel.reset(model());
        window.location.hash = "";
      }},
      {title: loc("no")}
    );
  }

  var plainModel = {
    personId: ko.observable(""),
    firstName: ko.observable(""),
    lastName: ko.observable(""),
    stamp: ko.observable(""),
    street: ko.observable("").extend({required: true}),
    city: ko.observable("").extend({required: true}),
    zip: ko.observable("").extend({required: true, number: true, maxLength: 5}),
    phone: ko.observable("").extend({required: true}),
    allowDirectMarketing: ko.observable(false),
    email: ko.observable("").extend({email: true}),
    password: ko.observable("").extend({validPassword: true}),
    rakentajafi: ko.observable(false),
    acceptTerms: ko.observable(false),
    disabled: ko.observable(true),
    submit: submit,
    cancel: cancel
  };
  plainModel.confirmPassword = ko.observable().extend({equal: plainModel.password});
  plainModel.confirmEmail = ko.observable().extend({equal: plainModel.email});

  var statusModel = new LUPAPISTE.StatusModel();

  model = ko.validatedObservable(plainModel);
  model.isValid.subscribe(function(valid) {
    model().disabled(!valid || !model().acceptTerms());
  });
  model().acceptTerms.subscribe(function() {
    model().disabled(!model.isValid() || !model().acceptTerms());
  });

  hub.onPageChange("register", function() {
    var urlPrefix = "/app/" + loc.getCurrentLanguage() + "/welcome";
    $.get("/api/vetuma", {success: urlPrefix + "#!/register2",
                          cancel:  urlPrefix + "#!/register/cancel",
                          error:   urlPrefix + "#!/register/error"}, function(d) {
      $("#vetuma-register")
        .html(d).find(":submit").addClass("btn btn-primary")
                                .attr("value",loc("register.action"))
                                .attr("id", "vetuma-init");
    });
    statusModel.subPage(pageutil.subPage());

  });

  hub.onPageChange("register2", function() {
    registrationModel.reset(model());
    confirmModel.email("");
    ajax.get("/api/vetuma/user")
      .raw(true)
      .success(function(data) {
        if (data) {
          model().personId(data.userid);
          model().firstName(data.firstName);
          model().lastName(data.lastName);
          model().stamp(data.stamp);
          model().city((data.city || ""));
          model().zip((data.zip || ""));
          model().street((data.street || ""));
        } else {
          window.location.hash = "!/register";
        }
      })
      .error(function(e){$("#register-email-error").text(loc(e.text));})
      .call();
  });

  $(function(){
    $("#register").applyBindings(statusModel);
    $("#register2").applyBindings(model);
    $("#register3").applyBindings(confirmModel);
  });

})();

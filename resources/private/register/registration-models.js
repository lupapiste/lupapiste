/**
 * Usage: Create new instance and bind 'model' property to Knockout bindings.
 * Username and password from user input are given to afterSuccessFn function.
 * Used keys can be set with optional last constructor parameter.
 */
LUPAPISTE.RegistrationModel = function(commandName, afterSuccessFn, errorSelector, ks) {
  "use strict";

  var self = this;

  self.keys = ks || ["stamp", "email", "language",
                     "street", "city", "zip", "phone", "password",
                     "allowDirectMarketing", "rakentajafi",
                     "architect", "degree", "graduatingYear", "fise", "fiseKelpoisuus"];

  var PlainModel = function(data, keys) {
    var self = this;

    var defaults = {
      personId: "",
      firstName: "",
      lastName: "",
      stamp: "",
      tokenId: pageutil.subPage(),
      street: "",
      city: "",
      zip: "",
      phone: "",
      language: loc.getCurrentLanguage(),
      email: "",
      password: "",
      degree: "",
      graduatingYear: "",
      fise: "",
      fiseKelpoisuus: "",
      architect: false,
      allowDirectMarketing: false,
      rakentajafi: false,
      acceptTerms: false
    };

    self.showRakentajafiInfo = function() {
      LUPAPISTE.ModalDialog.open("#dialogRakentajafi");
    };

    function json() {
      var data = _.pick(ko.mapping.toJS(self), keys);
      data.email = _.trim(data.email).toLowerCase();
      return data;
    }

    self.setData = function(data) {
      ko.mapping.fromJS(_.defaults(data, defaults), {}, self);
    };

    self.reset = function() {
      self.setData({});
      return false;
    };

    self.submit = function() {
      var error$ = $(errorSelector);
      error$.text("");
      self.pending(true);
      ajax.command(commandName, json())
        .success(function() {
          var email = _.clone(self.email());
          var password = _.clone(self.password());
          self.reset();
          self.email(email);
          self.pending(false);
          afterSuccessFn(email, password);
        })
        .error(function(e) {
          self.pending(false);
          error$.text(loc(e.text));
        })
        .call();
      return false;
    };

    self.cancel = function() {
      hub.send("show-dialog", {ltitle: "areyousure",
                               size: "medium",
                               component: "yes-no-dialog",
                               componentParams: {ltext: "register.confirm-cancel",
                                                 yesFn: function() {
                                                   self.reset();
                                                   self.canceled(true);
                                                   pageutil.openPage("register3");
                                                 }}});
    };

    ko.mapping.fromJS(_.defaults(data, defaults), {}, self);
    self.pending = ko.observable(false);
    self.canceled = ko.observable(false);

    self.street.extend({required: true, maxLength: LUPAPISTE.config.inputMaxLength});
    self.city.extend({required: true, maxLength: LUPAPISTE.config.inputMaxLength});
    self.zip.extend({required: true, number: true, maxLength: 5, minLength: 5});
    self.phone.extend({required: true, maxLength: LUPAPISTE.config.inputMaxLength});
    self.email.extend({email: true});
    self.password.extend({validPassword: true});
    self.degree.extend({maxLength: LUPAPISTE.config.inputMaxLength});
    self.graduatingYear.extend({number: true, minLength: 4, maxLength: 4});
    self.fise.extend({maxLength: LUPAPISTE.config.inputMaxLength});

    self.confirmPassword = ko.observable().extend({equal: self.password});
    self.confirmEmail = ko.observable().extend({equal: self.email});

    self.availableDegrees = _(LUPAPISTE.config.degrees).map(function(degree) {
      return {id: degree, name: loc(["koulutus", degree])};
    }).sortBy("name").value();
    self.availableFiseKelpoisuusValues = _(LUPAPISTE.config.fiseKelpoisuusValues).map(function(kelp) {
      return {id: kelp, name: loc(["fisekelpoisuus", kelp])};
    }).sortBy("name").value();
  };

  self.model = ko.validatedObservable(new PlainModel({}, self.keys));

  self.model().disabled = ko.computed(function() {
    return !(self.model.isValid() && self.model().acceptTerms() && lupapisteApp.models.globalAuthModel.ok(commandName));
  });

  self.reset = self.model().reset;

  self.setVetumaData = function(data) {
    // convert nulls to undefined
    var d = {
      personId: data.userid ? data.userid : undefined,
      firstName: data.firstName ? data.firstName : undefined,
      lastName: data.lastName ? data.lastName : undefined,
      stamp: data.stamp ? data.stamp : undefined,
      city: data.city ? data.city : undefined,
      zip: data.zip ? data.zip : undefined,
      street: data.street ? data.street : undefined
    };
    self.model().setData(d);
  };

  self.setPhone = function(phone) {
    self.model().phone(phone);
  };

  self.setEmail = function(email) {
    self.model().email(email);
    self.model().confirmEmail(email);
  };
};

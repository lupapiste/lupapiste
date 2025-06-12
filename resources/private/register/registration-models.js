/**
 * Usage: Create new instance and bind 'model' property to Knockout bindings.
 * Username and password from user input are given to afterSuccessFn function.
 * Used keys can be set with optional last constructor parameter.
 */
LUPAPISTE.RegistrationModel = function(commandName, afterSuccessFn, ks) {
  "use strict";

  var self = this;

  self.keys = ks || ["stamp", "email", "language",
                     "street", "city", "zip", "phone", "password",
                     "allowDirectMarketing",
                     "architect", "degree", "graduatingYear", "fise", "fiseKelpoisuus"];

  var PlainModel = function(data, keys) {
    var self = this;
    ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

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
      acceptTerms: false,
      eidasId: "",
      eidasFamilyName: "",
      eidasBirthDate: "",
      error: "",
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

    self.error = ko.observable();

    self.submit = function() {
      self.error( "" );
      self.pending(true);
      ajax.command(commandName, json())
        .pending( self.pending )
        .success(function() {
          var email = _.clone(self.email());
          var password = _.clone(self.password());
          self.reset();
          self.email(email);
          afterSuccessFn(email, password);
        })
        .error(function(e) {
          self.error( e.text );
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
    self.email.extend({required: true, email: true});
    self.password.extend({required: true, validPasswordLength: true});
    self.degree.extend({maxLength: LUPAPISTE.config.inputMaxLength});
    self.graduatingYear.extend({number: true, minLength: 4, maxLength: 4});
    self.fise.extend({maxLength: LUPAPISTE.config.inputMaxLength});

    self.confirmPassword = ko.observable().extend({required: true, equal: self.password});
    self.confirmEmail = ko.observable().extend({equal: self.email});

    self.showPasswords = ko.observable(false);

    self.passwordQuality = ko.observable("");
    self.passwordQuality.extend({validPasswordQuality: true});
    // Provide more details as the user entity has not yet been created
    self.passwordOptions = self.disposedComputed(function() {
      return {"password": self.password(),
              "firstName": self.firstName(),
              "lastName": self.lastName(),
              "email": self.email()};
    });

    self.disposedSubscribe(self.passwordOptions,
                           function (opts) {
                             util.getPwQualityAsync(opts.password, self.passwordQuality, opts);
    });

    self.passwordQualityInfo = ko.pureComputed(function() {
      return loc(["mypage.quality", self.passwordQuality()]);
    });

    self.availableDegrees = _(LUPAPISTE.config.degrees).map(function(degree) {
      return {id: degree, name: loc(["koulutus", degree])};
    }).sortBy("name").value();
    self.availableFiseKelpoisuusValues = _(LUPAPISTE.config.fiseKelpoisuusValues).map(function(kelp) {
      return {id: kelp, name: loc(["fisekelpoisuus", kelp])};
    }).sortBy("name").value();

    self.isRequired = util.isRequiredObservable;
    self.isNotValid = util.isNotValidObservable;
  };

  self.model = ko.validatedObservable(new PlainModel({}, self.keys));

  self.model().disabled = ko.computed(function() {
    return !(self.model.isValid() && self.model().acceptTerms() && lupapisteApp.models.globalAuthModel.ok(commandName));
  });

  self.reset = self.model().reset;

  // This only affects the UI, not the vetuma data handled in the backend
  self.setVetumaData = function(data) {
    // convert nulls to undefined
    var get = function(i) { return data[i] ? data[i] : undefined; };
    var get_either = function(i, j) { return data[i] ? get(i) : get(j); };
    var d = {
      personId:        get_either("userid", "eidasId"),
      lastName:        get_either("lastName", "eidasFamilyName"),
      firstName:       get("firstName"),
      stamp:           get("stamp"),
      city:            get("city"),
      zip:             get("zip"),
      street:          get("street"),
      eidasId:         get("eidasId"),
      eidasFamilyName: get("eidasFamilyName"),
      eidasBirthDate:  get("eidasBirthDate")
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

LUPAPISTE.RegistrationModel = function() {

  var self = this;
  self.keys = ["stamp", "personId", "firstName", "lastName", "email", "confirmEmail", "street", "city", "zip", "phone", "password", "confirmPassword", "street", "zip", "city", "allowDirectMarketing", "rakentajafi"];

  self.plainModel = {
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
    submit: null,
    cancel: null
  };
  self.plainModel.confirmPassword = ko.observable().extend({equal: self.plainModel.password});
  self.plainModel.confirmEmail = ko.observable().extend({equal: self.plainModel.email});

  self.model = ko.validatedObservable(self.plainModel);
  self.model.isValid.subscribe(function(valid) {
    self.plainModel.disabled(!valid || !self.plainModel.acceptTerms());
  });
  self.plainModel.acceptTerms.subscribe(function() {
    self.plainModel.disabled(!self.model.isValid() || !self.plainModel.acceptTerms());
  });

  self.json = function(model) {
    var d = {};
    _.forEach(self.keys, function(key) {
      d[key] = model[key]() || null;
    });

    d.confirmPassword = null;
    d.confirmEmail = null;
    return d;
  };

  self.reset = function(model) {
    _.forEach(self.keys, function(key) {
      if (model[key] !== undefined) {
        model[key]("");
        if (model[key].isModified) {
          model[key].isModified(false);
        }
      }
    });
    return false;
  };


};

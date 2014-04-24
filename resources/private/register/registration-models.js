LUPAPISTE.RegistrationModel = function() {

  var self = this;
  self.keys = ["stamp", "personId", "firstName", "lastName", "email", "confirmEmail", "street", "city", "zip", "phone", "password", "confirmPassword", "street", "zip", "city", "allowDirectMarketing", "rakentajafi"];


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

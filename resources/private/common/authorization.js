/*
 * authorization.js:
 */

var authorization = function() {

  function AuthorizationModel() {
    var self = this;

    self.data = ko.observable({})

    self.ok = function(command) {
      return self.data && self.data()[command] && self.data()[command].ok;
    }
  }

  return {
    create: function() {return new AuthorizationModel(); }
  };

}();

var authorization = (function() {
  "use strict";

  function AuthorizationModel() {
    var self = this;

    self.data = ko.observable({});

    self.ok = function(command) {
      return self.data && self.data()[command] && self.data()[command].ok;
    };

    self.refreshWithCallback = function(data, callback) {
      return ajax.query("allowed-actions", data)
        .success(function(d) {
          self.data(d.actions);
          if (callback) { callback(); }
        })
        .error(function(e) {
          self.data({});
          error(e);
        })
        .call();
    };

    self.refresh = function(data, extraParams) {
      var id = _.isObject(data) ? data.id : data;
      var params =  _.isObject(extraParams) ? _.extend({id: id}, extraParams) : {id: id};
      self.refreshWithCallback(params);
    };
  }

  var model = new AuthorizationModel();

  return {
    create: function() { return new AuthorizationModel(); },
    ok: function(command) { return model.ok(command); }
  };

})();

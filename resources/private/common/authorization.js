var authorization = (function() {
  "use strict";

  function AuthorizationModel() {
    var self = this;

    self.data = ko.observable({});

    self.ok = function(command) {
      var authz = self.data()[command];
      return authz && authz.ok;
    };

    self.clear = function() {
      self.data({});
    };

    self.refreshWithCallback = function(queryParams, callback) {
      return ajax.query("allowed-actions", queryParams)
        .success(function(d) {
          self.data(d.actions);
          if (callback) { callback(); }
        })
        .error(function(e) {
          self.clear();
          error(e);
        })
        .call();
    };

    self.refresh = function(queryParams, extraParams, callback) {
      var id = _.isObject(queryParams) ? queryParams.id : queryParams;
      var params =  _.isObject(extraParams) ? _.extend({id: id}, extraParams) : {id: id};
      self.refreshWithCallback(params, callback);
    };

    return {
      ok: self.ok,
      clear: self.clear,
      refreshWithCallback: self.refreshWithCallback,
      refresh: self.refresh
    };
  }

  return {
    create: function() { return new AuthorizationModel(); }
  };

})();

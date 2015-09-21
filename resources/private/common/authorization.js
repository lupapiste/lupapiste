var authorization = (function() {
  "use strict";

  function AuthorizationModel() {
    var self = this;

    self.data = ko.observable({});

    self.ok = function(command) {
      return self.data && self.data()[command] && self.data()[command].ok;
    };

    self.refreshWithCallback = function(queryParams, callback) {
      return ajax.query("allowed-actions", queryParams)
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

    self.refresh = function(queryParams, extraParams, callback) {
      var id = _.isObject(queryParams) ? queryParams.id : queryParams;
      var params =  _.isObject(extraParams) ? _.extend({id: id}, extraParams) : {id: id};
      self.refreshWithCallback(params, callback);
    };

    return {
      ok: self.ok,
      refreshWithCallback: self.refreshWithCallback,
      refresh: self.refresh
    };
  }

  return {
    create: function() { return new AuthorizationModel(); }
  };

})();

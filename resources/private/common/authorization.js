var authorization = (function() {
  "use strict";

  function AuthorizationModel(data) {
    var self = this;

    self.data = ko.observable(_.isObject(data) ? data : {});

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

    self.setData = function(data) {
      self.data(data);
    };

    self.clone = function() {
      return new AuthorizationModel( self.data() );
    };

    return {
      ok: self.ok,
      clear: self.clear,
      refreshWithCallback: self.refreshWithCallback,
      refresh: self.refresh,
      setData: self.setData,
      clone: self.clone
    };
  }

  // authModels is an object where, field names are ids and values the
  // actual auth models.  For example, for documents category, the
  // field names are document ids and values document auth models.
  function refreshModelsForCategory(authModels, applicationId, category, callback) {
    ajax.query("allowed-actions-for-category", {id: applicationId, category: category})
      .success(function(d) {
        _.forEach(authModels, function(authModel, id) {
          authModel.setData(d.actionsById[id] || {});
        });
        if (_.isFunction(callback)) { callback(d.result); }
      })
      .error(function(e) {
        _.forEach(authModels, function(authModel) {
          authModel.setData({});
        });
        error(e);
        if (_.isFunction(callback)) { callback(e.result); }
      })
      .call();
  }

  return {
    create: function(data) { return new AuthorizationModel(data); },
    refreshModelsForCategory: refreshModelsForCategory
  };

})();

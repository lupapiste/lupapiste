var authorization = (function() {
  "use strict";

  var usagePurpose = util.usagePurposeFromUrl();
  if (usagePurpose && usagePurpose.type === "authority-admin") {
    ajax.pushPreprocessor(function (params) { return _.extend({organizationId: usagePurpose.orgId}, params); });
  }

  function AuthorizationModel(data) {
    var self = this;

    self.data = ko.observable(_.isObject(data) ? data : {});
    self.isInitialized = ko.observable( false );

    var notifyChanged = _.debounce( function() {
      hub.send( "auth-model-changed" );
    }, 200);

    self.data.subscribe( notifyChanged );

    self.ok = function(command) {
      var authz = self.data()[command];
      // Make sure that the result is the same both for development
      // and production environments.
      return Boolean( authz && authz.ok );
    };

    self.okObservable = function (command) { return ko.pureComputed(_.partial(self.ok, command)); };

    self.clear = function() {
      self.data({});
    };

    self.refreshWithCallback = function(queryParams, callback) {
      return ajax.query("allowed-actions", queryParams)
        .success(function(d) {
          self.data(d.actions);
          self.isInitialized( true );
          if (callback) { callback(); }
        })
        .error(function(e) {
          self.clear();
          self.isInitialized( false );
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

    self.getData = function() {
      return self.data();
    };

    self.clone = function() {
      return new AuthorizationModel( self.data() );
    };

    return {
      ok: self.ok,
      okObservable: self.okObservable,
      clear: self.clear,
      refreshWithCallback: self.refreshWithCallback,
      refresh: self.refresh,
      setData: self.setData,
      getData: self.getData,
      clone: self.clone,
      isInitialized: self.isInitialized
    };
  }

  // authModels is an object where, field names are ids and values the
  // actual auth models.  For example, for documents category, the
  // field names are document ids and values document auth models.
  function refreshModelsForCategory(authModels, applicationId, category, callback) {
    ajax.query("allowed-actions-for-category", {id: applicationId, category: category})
      .success(function(d) {
        _.forEach(authModels, function(authModel, id) {
          var oldData = authModel.getData();
          var newData = d.actionsById[id] || {};
          authModel.setData(newData);
          if (!_.isEqual(oldData, newData)) {
            hub.send("category-auth-model-changed", {targetId: id,
                                                     category: category});
          }
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

LUPAPISTE.ForemanOtherApplicationsModel = function(params) {
  "use strict";
  var self = this;
  self.params = params;
  self.rows = ko.observableArray();
  self.autoupdatedRows = ko.observableArray();
  self.disableWhileUpdating = ko.observable(false);

  var service = lupapisteApp.services.documentDataService;

  // inherit from DocgenGroupModel
  ko.utils.extend(self, new LUPAPISTE.DocgenRepeatingGroupModel(params));

  self.rows = ko.computed(function() {
    return _.reject(self.groups(), function(group) {
      return service.getValueIn(group, ["autoupdated"]);
    });
  });

  self.autoupdatedRows = ko.computed(function() {
    return _.filter(self.groups(), function(group) {
      return service.getValueIn(group, ["autoupdated"]);
    });
  });


  self.subSchemas = _.map(params.schema.body, function(schema) {
    var i18npath = schema.i18nkey ? [schema.i18nkey] : params.i18npath.concat(schema.name);
    return _.extend({}, schema, {
      schemaI18name: params.schemaI18name,
      i18npath: i18npath,
      applicationId: params.applicationId,
      documentId: params.documentId,
      readonly: false,
      label: !!schema.label
    });
  });

  self.subSchemasAutoupdated = _.map(self.subSchemas, function(schema) {
    var readonly = _.includes(LUPAPISTE.config.foremanReadonlyFields, schema.name);
    return _.extend({}, schema, {
      readonly: readonly,
      inputType: readonly && schema.locPrefix ? "localized-string" : schema.inputType
    });
  });

  self.subscriptionIds = [];
  self.subscriptionIds.push(hub.subscribe("documentChangedInBackend", function(data) {
    if (data.documentName === self.params.documentName) {
      ajax.command("update-foreman-other-applications", {id: self.params.applicationId})
      .success(function() {
        repository.load(self.params.applicationId);
      })
      .call();
    }
  }));

  self.subscriptionIds.push(hub.subscribe("hetuChanged", function() {
    self.disableWhileUpdating(true);
    ajax.command("update-foreman-other-applications", {id: self.params.applicationId})
    .success(function() {
      repository.load(self.params.applicationId);
    })
    .call();
  }));

  // unsubscribe hub listeners on application load
  hub.subscribe("application-loaded", function() {
    while (self.subscriptionIds.length > 0) {
      hub.unsubscribe(self.subscriptionIds.pop());
    }
  }, true);
};

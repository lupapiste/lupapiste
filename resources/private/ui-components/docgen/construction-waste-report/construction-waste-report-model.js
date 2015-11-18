LUPAPISTE.ConstructionWasteReportModel = function(params) {
  "use strict";
  var self = this;

  self.service = lupapisteApp.services.documentDataService;

  var data = self.service.getInDocument(params.documentId, params.path);
  var plannedWasteData = data.model.suunniteltuJate;
  var unplannedWasteData = data.model.suunnittelematonJate;

  self.combinedGroups = ko.computed(function() {
    return plannedWasteData.model().concat(unplannedWasteData.model());
  });

  // inherit unplanned waste from DocgenGroupModel
  ko.utils.extend(self, new LUPAPISTE.DocgenRepeatingGroupModel(_.extend({}, params, unplannedWasteData)));

  self.groupId = ["table", params.documentId].concat(params.path).join("-");
  self.groupLabel = params.i18npath.concat("_group_label").join(".");
  self.groupHelp = params.schema["group-help"] && params.i18npath.concat(params.schema["group-help"]).join(".");

  self.buildSubSchemas = function(groupSchema) {
    var schemaMapper = _(groupSchema.body).map('name').zipObject(groupSchema.body).value();
    return _.map(plannedWasteData.schema.body, function(schema) {
      var uicomponent = "docgen-" + schema.type;
      var path = params.path.concat([groupSchema.name, schema.name]);
      var i18npath = schema.i18nkey ? [schema.i18nkey] : params.i18npath.concat([groupSchema.name, schema.name]);
      return _.extend({}, schemaMapper[schema.name], {
        renderElement: !!schemaMapper[schema.name],
        path: path,
        uicomponent: uicomponent,
        schemaI18name: params.schemaI18name,
        i18npath: i18npath,
        applicationId: params.applicationId,
        documentId: params.documentId,
        label: false
      });
    });
  }

  self.columnHeaders = _.map(plannedWasteData.schema.body, function(schema) {
    return params.i18npath.concat(schema.name);
  });

  self.columnHeaders.push("remove");
};
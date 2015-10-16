LUPAPISTE.DocgenGroupModel = function(params) {
  "use strict";
  var self = this;

  var model = params.model || {};

  self.rows = ko.observableArray();

  self.params = params;

  self.applicationId = params.applicationId;
  self.documentId = params.documentId;
  self.path = _.isArray(params.path) ? params.path : [params.path];
  self.i18npath = params.i18npath;
  self.groupId = "group-" + params.documentId + "-" + self.path.join("-");
  self.groupLabel = self.i18npath.concat("_group_label").join(".");

  var subSchemas = _.map(params.subSchema.body, function(schema) {
    var uicomponent = schema.uicomponent || "docgen-" + schema.type;
    var isSelect = schema.uicomponent === "docgen-select" || schema.type === "select";
    var hasLabel = schema.label === "false";
    var i18npath = schema.i18nkey ? [schema.i18nkey] : params.i18npath.concat(schema.name);
    var locKey = i18npath.join(".");
    return _.extend({}, schema, {
      uicomponent: uicomponent,
      path: self.path.concat(schema.name),
      schemaI18name: self.schemaI18name,
      i18npath: i18npath,
      label: hasLabel ? null : locKey,
      applicationId: self.applicationId,
      documentId: params.documentId,
      valueAllowUnset: isSelect ? true : undefined,
    });
  });

  var createRow = function(model, index) {
    return {subSchemas: _.map(subSchemas, function(schema) {
      return _.extend({}, schema, {
        path: index ? schema.path.concat(index) : schema.path,
        model: model[schema.name]
      });
    })};
  }

  if (params.subSchema.repeating) {
    self.rows(_.map(model, createRow));
  } else {
    self.rows([createRow(model)]);
  }
};
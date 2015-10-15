LUPAPISTE.DocgenGroupModel = function(params) {
  "use strict";
  var self = this;

  self.path = _.isArray(params.path) ? params.path : [params.path];
  self.groupId = "group-" + params.documentId + "-" + self.path.join("-");
  self.groupLabel = self.path.join(".") + "._group_label";
  self.applicationId = params.applicationId || lupapisteApp.models.application.id();
  self.documentId = params.documentId;

  var model = params.model || {};

  function buildLocKey(subSchema, path) {
    return subSchema.i18nkey || path.join(".");
  }

  self.subSchemas = _.map(params.subSchema.body, function(schema) {
    return _.extend(schema, {
      uicomponent: schema.uicomponent || "docgen-" + schema.type,
      path: self.path,
      label: schema.label === false ? null : [buildLocKey(params.subSchema, self.path), schema.name].join("."),
      model: model[schema.name],
      valueAllowUnset: schema.uicomponent === "docgen-select" || schema.type === "select" ? true : undefined
    });
  });
};
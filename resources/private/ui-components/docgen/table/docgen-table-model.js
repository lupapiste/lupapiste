LUPAPISTE.DocgenTableModel = function(params) {
  "use strict";
  var self = this;

  console.log(params);

  self.path = _.isArray(params.path) ? params.path : [params.path];
  self.applicationId = params.applicationId || lupapisteApp.models.application.id();
  self.documentId = params.documentId;
  self.groupId = "table-" + params.documentId + "-" + self.path.join("-");
  self.groupLabel = self.path.join(".") + "._group_label";

  var model = params.model || {};

  function buildLocKey(subSchema, path) {
    return subSchema.i18nkey || path.concat(subSchema.name).join(".");
  }

  self.columnHeaders = _.map(params.subSchema.body, function(schema) {
    return self.path.concat(schema.name);
  });

  self.subSchemas = _.map(params.subSchema.body, function(schema) {
    var subSchemaPath = self.path.concat(schema.name);
    var label = schema.label === false ? null : [buildLocKey(params.subSchema, self.path), schema.name].join(".");
    if (schema.type === "select") {
      schema = _.extend(schema, {valueAllowUnset: true});
    }
    return _.extend(schema, {
      uicomponent: schema.uicomponent || "docgen-" + schema.type,
      path: self.path,
      label: label,
      model: model[schema.name]
    });
  });

  console.log(self.subSchemas);
};
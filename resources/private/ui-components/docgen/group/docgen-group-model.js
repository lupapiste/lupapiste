LUPAPISTE.DocgenGroupModel = function(params) {
  "use strict";
  var self = this;

  self.path = _.isArray(params.path) ? params.path : [params.path];
  self.groupId = "group-" + params.documentId + "-" + self.path.join("-");
  self.groupLabel = self.path.join(".") + "._group_label";

  function buildLocKey(subSchema, path) {
    return subSchema.i18nkey || path.concat(subSchema.name).join(".");
  }

  self.subSchemas = _.map(params.subSchema.body, function(schema) {
    var subSchemaPath = self.path.concat(schema.name);
    return _.extend(schema, {
      path: subSchemaPath,
      label: [buildLocKey(params.subSchema, self.path), schema.name].join("."),
      model: params.model[schema.name]
    });
  });
};
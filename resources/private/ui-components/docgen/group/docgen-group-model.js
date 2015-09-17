LUPAPISTE.DocgenGroupModel = function(params) {
  "use strict";
  var self = this;

  self.path = _.isArray(params.path) ? params.path : [params.path];
  self.groupId = "group-" + params.documentId + "-" + self.path.join("-");
  self.groupLabel = self.path.join(".") + "._group_label";

  function buildI18nPath(subSchema, path) {
    var i18nPath = subSchema.i18nkey ?
      path.slice(0, -1).concat(subSchema.i18nkey) :
      path;
    return i18nPath.join(".");
  }

  self.subSchemas = _.map(params.subSchema.body, function(schema) {
    var subSchemaPath = self.path.concat(schema.name);
    return _.extend(schema, {
      path: subSchemaPath,
      label: [buildI18nPath(params.subSchema, self.path), schema.name].join("."),
      model: params.model[schema.name]
    });
  });
};
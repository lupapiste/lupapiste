LUPAPISTE.DocgenTableModel = function(params) {
  "use strict";
  var self = this;

  // Label defaults to false (not visible) in table subcomponents
  params.schema.body = _.map(params.schema.body, function(schema) {
    return _.extend(schema, {label: !!schema.label});
  });

  // inherit from DocgenGroupModel
  ko.utils.extend(self, new LUPAPISTE.DocgenRepeatingGroupModel(params));

  self.groupId = ["table", params.documentId].concat(self.path).join("-");
  self.groupLabel = params.i18npath.concat("_group_label").join(".");
  self.groupHelp = params.schema["group-help"] && params.i18npath.concat(params.schema["group-help"]).join(".");

  self.columnHeaders = _.map(params.schema.body, function(schema) {
    return params.i18npath.concat(schema.name);
  });

  self.columnHeaders.push("remove");

  var subSchemas = _.map(params.schema.body, function(schema) {
    var uicomponent = schema.uicomponent || "docgen-" + schema.type;
    var i18npath = schema.i18nkey ? [schema.i18nkey] : params.i18npath.concat(schema.name);
    return _.extend({}, schema, {
      uicomponent: uicomponent,
      schemaI18name: params.schemaI18name,
      i18npath: i18npath,
      applicationId: params.applicationId,
      documentId: params.documentId
    });
  });

  self.rows = ko.computed(function() {
    return _.map(self.groups(), function(group) {
      return _.extend({}, group, {
        subSchemas: _.map(subSchemas, function(schema) {
          return _.extend({}, schema, {
            model: group.model[schema.name],
            path: group.path.concat(schema.name)
          });
        })
      });
    });
  });

};

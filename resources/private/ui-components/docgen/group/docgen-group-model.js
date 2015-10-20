LUPAPISTE.DocgenGroupModel = function(params) {
  "use strict";
  var self = this;

  var model = params.model || {};

  self.rows = ko.observableArray();

  self.repeating = params.schema.repeating;

  self.params = params;

  self.applicationId = params.applicationId;
  self.documentId = params.documentId;
  self.path = _.isArray(params.path) ? params.path : [params.path];
  self.i18npath = params.i18npath;
  self.groupId = "group-" + params.documentId + "-" + self.path.join("-");
  self.groupLabel = self.i18npath.concat("_group_label").join(".");
  self.appendLabel = self.i18npath.concat("_append_label").join(".");

  self.indicator = ko.observable().extend({notify: "always"});
  self.result = ko.observable().extend({notify: "always"});

  self.subSchemas = _.map(params.schema.body, function(schema) {
    var uicomponent = schema.uicomponent || "docgen-" + schema.type;
    var isSelect = schema.uicomponent === "docgen-select" || schema.type === "select";
    var hasLabel = schema.label !== false && schema.label !== "false";
    var i18npath = schema.i18nkey ? [schema.i18nkey] : params.i18npath.concat(schema.name);
    var locKey = i18npath.join(".");
    return _.extend({}, schema, {
      uicomponent: uicomponent,
      schemaI18name: self.schemaI18name,
      i18npath: i18npath,
      label: hasLabel ? locKey : null,
      applicationId: self.applicationId,
      documentId: params.documentId,
      valueAllowUnset: isSelect ? true : undefined,
    });
  });

  var createRow = function(rowModel, index) {
    var groupPath = index ? self.path.concat(index) : self.path;
    return { index: index,
             subSchemas: _.map(self.subSchemas, function(schema) {
      return _.extend({}, schema, {
        path: groupPath.concat(schema.name),
        model: rowModel[schema.name]
      });
    })};
  }

  self.removeRow = function(rowIndex) {
    self.rows.splice(rowIndex, 1);
  }

  self.addRow = function() {
    var dataIndex = parseInt( _(self.rows()).map('index').max() ) + 1;
    self.rows.push(createRow({}, dataIndex));
  }

  if (self.repeating) {
    self.rows(_.map(model, createRow));
  } else {
    self.rows([createRow(model)]);
  }
};
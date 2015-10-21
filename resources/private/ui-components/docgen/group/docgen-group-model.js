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
  self.groupHelp = params.schema['group-help'] && self.i18npath.concat(params.schema['group-help']).join(".");

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
    var groupPath = _.isNaN(parseInt(index)) ? self.path : self.path.concat(index);
    return { index: index,
             subSchemas: _.map(self.subSchemas, function(schema) {
      return _.extend({}, schema, {
        path: groupPath.concat(schema.name),
        model: rowModel[schema.name]
      });
    })};
  }

  self.removeRow = function(row) {
    var path = self.params.path.concat(row.index);
    var row = _.find(self.rows(), function(r) { return r.index === row.index });

    var removeFn = function () {
      ajax
        .command("remove-document-data", {
          doc: self.documentId,
          id: self.applicationId,
          path: path,
          collection: "documents"
        })
        .success(function() {
          self.rows.remove(row);
        })
        .call();
    };

    var message = "document.delete." + params.schema.type + ".row.message";

    hub.send("show-dialog", {ltitle: "document.delete.header",
                             size: "medium",
                             component: "yes-no-dialog",
                             componentParams: {ltext: message,
                                               yesFn: removeFn}});
  }

  self.addRow = function() {
    var dataIndex = parseInt( _(self.rows()).map('index').max() ) + 1;
    self.rows.push(createRow({}, dataIndex || 0));
  }

  if (self.repeating) {
    self.rows(_.map(model, createRow));
  } else {
    self.rows([createRow(model)]);
  }

  var addOneIfEmpty = function(rows) {
    if ( _.isEmpty(rows) ) {
      self.addRow();
    }
  }
  self.rows.subscribe(addOneIfEmpty);
  addOneIfEmpty(self.rows());
};
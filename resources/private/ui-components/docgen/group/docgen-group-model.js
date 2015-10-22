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
  self.copyLabel = self.i18npath.concat("_copy_label").join(".");
  self.groupHelp = params.schema['group-help'] && self.i18npath.concat(params.schema['group-help']).join(".");

  self.indicator = ko.observable().extend({notify: "always"});
  self.result = ko.observable().extend({notify: "always"});

  self.subSchemas = _.map(params.schema.body, function(schema) {
    var uicomponent = schema.uicomponent || "docgen-" + schema.type;
    var i18npath = schema.i18nkey ? [schema.i18nkey] : params.i18npath.concat(schema.name);
    return _.extend({}, schema, {
      uicomponent: uicomponent,
      schemaI18name: self.schemaI18name,
      i18npath: i18npath,
      applicationId: self.applicationId,
      documentId: params.documentId,
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

    var cb = function () {
      var r = _.find(self.rows(), function(r) {
        return r.index === row.index;
      });
      self.rows.remove(r);
    };

    var removeFn = function () {
      uiComponents.removeRow(self.documentId, self.applicationId, path, self.indicator, self.result, cb);
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

  self.duplicateLastRow = function() {
    var sourceIndex = parseInt( _(self.rows()).map('index').max() );
    uiComponents.copyRow(self.documentId,
                         self.applicationId,
                         self.path,
                         sourceIndex,
                         sourceIndex + 1,
                         self.indicator,
                         self.result);
  };

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
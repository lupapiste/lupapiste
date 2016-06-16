LUPAPISTE.DocgenGroupModel = function(params) {
  "use strict";
  var self = this;

  self.path = _.isArray(params.path) ? params.path : [params.path];
  self.applicationId = params.applicationId;
  self.documentId = params.documentId;
  self.service = params.service || lupapisteApp.services.documentDataService;
  self.schemaRows = params.schema.rows;
  self.authModel = params.authModel || lupapisteApp.models.applicationAuthModel;
  self.componentTemplate = (params.template || params.schema.template) || "default-docgen-group-template";

  self.groupId = ["group", params.documentId].concat(self.path).join("-");
  self.groupLabel = params.i18npath.concat("_group_label").join(".");
  self.groupHelp = params.schema["group-help"] && params.i18npath.concat(params.schema["group-help"]).join(".");

  self.indicator = ko.observable().extend({notify: "always"});
  self.result = ko.observable().extend({notify: "always"});

  self.subSchemas = _.map(params.schema.body, function(schema) {
    var uicomponent = schema.uicomponent || "docgen-" + schema.type;
    var i18npath = schema.i18nkey ? [schema.i18nkey] : params.i18npath.concat(schema.name);
    var finalschema = _.extend({}, schema, {
      path: self.path.concat(schema.name),
      uicomponent: uicomponent,
      schemaI18name: params.schemaI18name,
      i18npath: i18npath,
      applicationId: params.applicationId,
      documentId: params.documentId,
      service: self.service
    });
    return finalschema;
  });

  function getInSchema(schema, path) {
    var pathArray = _.isArray(path) ? path : path.split("/");
    if (_.isEmpty(pathArray)) {
      return schema;
    } else {
      return getInSchema(_.find(schema.body, {name: _.head(pathArray)}), _.tail(pathArray));
    }
  }

  self.getColClass = function(schema) {
    return "col-" + schema.cols;
  };

  self.rowSchemas = _.map(self.schemaRows, function(row) {
    if ( _.isArray(row)) {
      return _.map(row, function(schemaName) {
        var splitted = schemaName.split("::");
        var cols = splitted[1] || 1;
        var schema = getInSchema(params.schema, splitted[0]);
        var path = self.path.concat(splitted[0].split("/"));
        return _.extend({}, schema, {
          path: path,
                  uicomponent: schema.uicomponent || "docgen-" + schema.type,
                  schemaI18name: params.schemaI18name,
                  i18npath: schema.i18nkey ? [schema.i18nkey] : params.i18npath.concat(path),
                  applicationId: params.applicationId,
                  documentId: params.documentId,
                  service: self.service,
                  cols: cols
                });
      });
    } else {
      var headerTag = _.head(_.keys(row));
      var ltext = row[headerTag];
      return {ltext: ltext, css: _.fromPairs( [[headerTag, true]])};
    }
  });

};

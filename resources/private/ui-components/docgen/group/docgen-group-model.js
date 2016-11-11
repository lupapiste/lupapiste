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

  function getValueByPathString(pathString) {
    var path = pathString.split("/");
    var absolutePath = path[0] === "" ? _.tail(path) : self.path.concat(path);
    return util.getIn(self.service.getInDocument(self.documentId, absolutePath), ["model"]);
  }

  self.subSchemas = ko.pureComputed(function() {
    return _(params.schema.body)
      .reject(function(schema) {
        var hideWhen = schema["hide-when"];
        return hideWhen ? _.includes(hideWhen.values, getValueByPathString(hideWhen.path)) : false;
      })
      .filter(function(schema) {
        var showWhen = schema["show-when"];
        return showWhen ? _.includes(showWhen.values, getValueByPathString(showWhen.path)) : true;
      })
      .map(function(schema) {
    var uicomponent = schema.uicomponent || "docgen-" + schema.type;
    var i18npath = schema.i18nkey ? [schema.i18nkey] : params.i18npath.concat(schema.name);
    return _.extend({}, schema, {
      path: self.path.concat(schema.name),
      uicomponent: uicomponent,
      schemaI18name: params.schemaI18name,
      i18npath: i18npath,
      applicationId: params.applicationId,
      documentId: params.documentId,
      service: self.service
    });
      }).value();
  });

  function getInSchema(schema, path) {
    var pathArray = _.isArray(path) ? path : path.split("/");
    if (!schema || _.isEmpty(pathArray)) {
      return schema;
    } else {
      return getInSchema(_.find(schema.body, {name: _.head(pathArray)}), _.tail(pathArray));
    }
  }

  self.getColClass = function(schema) {
    return "col-" + schema.cols;
  };

  self.rowSchemas = ko.pureComputed(function() {
    return _(self.schemaRows)
      .map(function(row) {
    if ( _.isArray(row)) {
      return _(row)
       .map(function(schemaName) {
        var splitted = schemaName.split("::");
        var cols = splitted[1] || 1;
        var schema = getInSchema({body: self.subSchemas()}, splitted[0]);
        var path = self.path.concat(splitted[0].split("/"));
        return schema && _.extend({}, schema, {
          path: path,
                  uicomponent: schema.uicomponent || "docgen-" + schema.type,
                  schemaI18name: params.schemaI18name,
                  i18npath: schema.i18nkey ? [schema.i18nkey] : params.i18npath.concat(splitted[0].split("/")),
                  applicationId: params.applicationId,
                  documentId: params.documentId,
                  service: self.service,
                  cols: cols
                });
      })
            .filter()
            .value();
    } else {
      var headerTag = _.head(_.keys(row));
      var ltext = row[headerTag];
      return {ltext: ltext, css: _.fromPairs( [[headerTag, true]])};
    }
  })
      .reject(_.isEmpty)
      .value();
  });

};

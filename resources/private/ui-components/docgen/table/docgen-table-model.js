LUPAPISTE.DocgenTableModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  // inherit from DocgenGroupModel
  ko.utils.extend(self, new LUPAPISTE.DocgenRepeatingGroupModel(params));

  self.schemaCss = self.schemaCss || "form-table";
  self.componentTemplate = (params.template || params.schema.template) || "default-docgen-table-template";

  self.groupId = ["table", params.documentId].concat(self.path).join("-");
  self.groupLabel = params.i18npath.concat("_group_label").join(".");
  self.groupHelp = params.schema["group-help"];

  self.authModel = params.authModel;

  self.columnName = function( schema ) {
    return params.i18npath.concat(schema.name);
  };

  self.columnHeaders = _.map(params.schema.body, function(schema) {
    return {
      name: self.columnName( schema ),
      required: Boolean(schema.required)
    };
  });
  self.columnHeaders.push({
    name: self.groupsRemovable(params.schema) ? "remove" : "",
    required: false
  });

  self.columnCss = function( nameArray ) {
    return util.arrayToObject( _.get( params.schema.columnCss,
                                      _.last(nameArray) ));
  };

  self.subSchemas = _.map(params.schema.body, function(schema) {
    var uicomponent = schema.uicomponent || "docgen-" + schema.type;
    var i18npath = schema.i18nkey ? [schema.i18nkey] : params.i18npath.concat(schema.name);
    return _.extend({}, schema, {
      uicomponent: uicomponent,
      schemaI18name: params.schemaI18name,
      i18npath: i18npath,
      applicationId: params.applicationId,
      documentId: params.documentId,
      label: !!schema.label
    });
  });

  function findFooter( column ) {
    return _.find( _.get( params, "schema.footer-sums"),
                   function( item ) {
                     return item === column
                       || item.amount === column;
                   });
  }

  self.footerSums = _(params.schema.body)
    .drop( 1 )  // The first column is sum header
    .map( function( schema ) {
      var footer = findFooter( schema.name );
      return footer
        ? {schema: schema,
           tdStyle: schema.type === "calculation" ? "td-center" : "td-left",
           footer: footer}
        : null;
    })
    .value();

  self.rows = self.disposedPureComputed( function() {
    var doc = lupapisteApp.services.documentDataService.findDocumentById( self.documentId );
    var columns = _.map( params.schema.body, "name" );
    return _.map( self.groups(), function( group ) {
      group.warnings = self.disposedPureComputed( function() {
        return _.sortBy(docutils.updateWarnings( doc, group.path, doc.validationResults() ),
                        function ( warn ) {
                          return _.indexOf( columns, _.last( warn.path ) );
                        });
      });
      return group;
    });
  });
};

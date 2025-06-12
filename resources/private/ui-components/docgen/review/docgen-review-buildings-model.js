LUPAPISTE.DocgenReviewBuildingsModel = function( params ) {
  "use strict";
  var self = this;

  self.authModel = params.authModel;
  self.service = params.service;
  var data = self.service.getInDocument( params.documentId,
                                         _.flatten( [params.path])).model();

  // The description cell value consists of two parts:
  // Tag and description, either one or both can be
  // missing.
  function buildingDescription( building ) {
    var nationalId = building.valtakunnallinenNumero.model();
    var index = building.jarjestysnumero.model();
    var appData = lupapisteApp.models.application._js;
    var description = "";
    var build = null;
    // Matching order:
    // 1. nationalId and index
    // 2. nationalId
    // 3. index
    if( nationalId || index ) {
      if( nationalId && index ) {
        build = _.find( appData.buildings, {nationalId: nationalId,
                                            index: index});
      }
      if( !build ) {
        build = _.find(appData.buildings,
                       nationalId ? {nationalId: nationalId } : {index: index });
      }
    }
    if( build ) {
      description = build.description || "";
      var doc = _.find( appData.documents,
                        function( doc ) {
                          var opId = _.get( doc, "schema-info.op.id");
                          var natId = _.get( doc, "data.valtakunnallinenNumero.value");
                          return (opId && opId === build.operationId)
                            || (natId && natId === nationalId);
                        });
      description = _.filter( [_.get( doc, "data.tunnus.value"),
                               description], _.identity ).join( ": ");

    }
    return description;
  }

  function subSchema( sub ) {
    var schema = sub.schema;
    var uicomponent = schema.uicomponent || "docgen-" + schema.type;
    var i18npath = schema.i18nkey ? [schema.i18nkey] : params.i18npath.concat(schema.name);
    var finalschema = _.extend({}, schema, {
      path: sub.path,
      uicomponent: uicomponent,
      label: false,
      schemaI18name: params.schemaI18name,
      i18npath: i18npath,
      applicationId: params.applicationId,
      documentId: params.documentId
    });
    return finalschema;
  }

  self.buildings = _.map( data, function( item ) {
    var build = item.model.rakennus.model;
    var state = item.model.tila.model;
    return {
      description: buildingDescription( build ),
      nationalId: build.valtakunnallinenNumero.model(),
      propertyId: build.kiinttun.model(),
      kayttoonottava: subSchema(state.kayttoonottava),
      tila: subSchema(state.tila)
    };
  });
};

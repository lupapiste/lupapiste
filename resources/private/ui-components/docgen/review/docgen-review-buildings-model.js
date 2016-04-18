LUPAPISTE.DocgenReviewBuildingsModel = function( params ) {
  "use strict";
  var self = this;

  self.authModel = params.authModel;
  self.service = params.service;
  var data = self.service.getInDocument( params.documentId,
                                         _.flatten( [params.path])).model();

 function buildingDescription( nationalId ) {
    var build = _.find( lupapisteApp.models.application._js.buildings,
                        {nationalId: nationalId });
    return build ? build.description || "" : "";
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
    var nationalId = build.valtakunnallinenNumero.model();
    return {
      description: buildingDescription( nationalId ),
      nationalId: nationalId,
      propertyId: build.kiinttun.model(),
      kayttoonottava: subSchema(state.kayttoonottava),
      tila: subSchema(state.tila)
    };
  });
};

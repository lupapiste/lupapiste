LUPAPISTE.DocgenBuildingSelectModel = function( params ) {
  "use strict";
  var self = this;

  var OTHER = "other";

  ko.utils.extend( self, new LUPAPISTE.DocgenInputModel( params ));

  var service = lupapisteApp.services.buildingService;

  self.buildingOptions = self.disposedComputed( function() {
    var infosObservable = service.buildingsInfo();
    var infos = infosObservable() || [];
    return _.concat( infos , [{buildingId: OTHER}]);
  });

  // Notify service that application has changed.
  self.addHubListener( "contextService::enter", service.buildingsInfo);

  var textTemplate = _.template("<%- buildingId %> (<%- usage %>) - <%- created %>");

  self.optionsText = function( building ) {
    return building.buildingId === OTHER
      ? loc( "select-other")
      : textTemplate( building );
  };

  // Id is needed for focusing support when following a link from the
  // required fields summary tab.
  self.otherId = sprintf( "%s-%s",
                          params.documentId,
                          _.get( params, "schema.other-key"));
  self.otherParams = _.merge( {documentId: params.documentId},
                              _.pick( lupapisteApp.services.documentDataService
                                      .getInDocument( params.documentId,
                                                      [params.schema["other-key"]]),
                                      ["path", "schema"]) );
  self.otherSelected = self.disposedPureComputed( function() {
    return ko.unwrap( self.value ) === OTHER;
  });

  function merge(overwrite) {
    hub.send( "buildingService::merge",
              {documentId: self.documentId,
               buildingId: self.value(),
               overwrite: overwrite});
  }

  function emitAccordionUpdates(updates) {
    _.forEach(updates, function(update) {
      hub.send("accordionUpdate", {path: update[0], value: update[1], docId: params.documentId});
    });
  }

  self.disposedSubscribe( self.value, function( value) {
    var documentDataService = lupapisteApp.services.documentDataService;
    if (value) {
      if (value === OTHER) {
        var accordionFields = _.get(documentDataService.findDocumentById(params.documentId), "schema.accordion-fields");
        var updates = _.map(accordionFields, function(path) { return [path, ""]; });
        documentDataService.updateDoc(params.documentId, updates, emitAccordionUpdates(updates));
      } else {
        LUPAPISTE.ModalDialog.showDynamicYesNo(
          loc("overwrite.confirm"),
          loc("application.building.merge"),
          {title: loc("yes"), fn: _.partial(merge, true)},
          {title: loc("no"), fn: _.partial(merge, false)}
        );
      }
    }
  });
};

// Select one of the available application kinds (fetched from Allu).
// Params [optional]:
// value: Observable for selected kind
// applicationType: String (typically 'short-term-rental'). Different
// types can have different kinds.
// [testId]: Test id for the select
// [enable]: from EnableComponentModel
// [disable]: from EnableComponentModel
//
// The possible application types and kinds are listed in allu/core.clj.
LUPAPISTE.AlluApplicationKindModel = function( params ) {
  "use strict";

  var self = this;

  ko.utils.extend( self, new LUPAPISTE.EnableComponentModel( params ));

  self.testId = params.testId;
  self.value = params.value;
  self.kinds = ko.observableArray();

  function fetchKinds() {
    if( lupapisteApp.models.applicationAuthModel.ok( "application-kinds")) {
      ajax.query( "application-kinds",
                  {id: lupapisteApp.services.contextService.applicationId(),
                   type: params.applicationType})
        .success( function( res ) {
          self.kinds( res.kinds );
        })
        .call();
    }
  }

  self.kindText = function( kind ) {
    return s.isBlank( kind ) ? "" : loc( "allu.application-kind." + kind );
  };

  // Initialization
  fetchKinds();
};

LUPAPISTE.ApplicationsMapTogglesModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var viewMode = params.viewMode;
  self.selected = params.selected;
  self.toggles = ko.observableArray();

  function authed( cmd ) {
    return lupapisteApp.models.globalAuthModel.ok( cmd );
  }

  function initialize() {
    var pureDigitizer = authed( "user-is-pure-digitizer");
    var finAuthority = lupapisteApp.models.currentUser.isFinancialAuthority();
    var archive = authed( "archiving-operations-enabled" );
    var digitizer = authed( "digitizing-enabled" );
    var foreman = viewMode === "foreman" && authed( "enable-foreman-search");
    var pureYmpOrgUser = authed( "user-is-pure-ymp-org-user");

    var tabs = [{value: "all", included: true},
                {value: "application", included: !(pureDigitizer || foreman)},
                {value: "foremanApplication", included: foreman && !pureDigitizer},
                {value: "foremanNotice", included: foreman && !pureDigitizer},
                {value: "construction", included: !(pureDigitizer || foreman || pureYmpOrgUser)},
                {value: "verdict", included: !pureDigitizer},
                {value: "finalized", included: !pureDigitizer},
                {value: "inforequest", included: !(finAuthority || pureDigitizer)},
                {value: "canceled", included: !pureDigitizer},
                {value: "readyForArchival", included: archive},
                {value: "archivingProjects", included: digitizer}];

    self.toggles( _( tabs )
                  .filter( "included")
                  .map( function( t ) {
                    return {value: t.value,
                            lText: "applications.filter." + t.value};
                  })
                  .value());
  }

  initialize();
};

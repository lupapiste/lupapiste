// Component that shows the given lhtml when the
// help icon is clicked.
// Parameters [optional]:
//  [show]: (Boolean) if given and true, the help is expanded by default.
//  [flag]: (ko.observable Boolean) if given, then the component
//          syncs its state with it. This is useful if the help
//          needs to be toggled outside of component.
//  lhtml:  (String id) identifier for lhtml binding. Can also be an array
//          of (paragraph) identifiers.

LUPAPISTE.HelpToggleModel = function( params ) {
  "use strict";
  var self = this;

  self.flag = params.flag || ko.observable( params.show );
  self.toggleHelp = function() {
    self.flag( !self.flag() );
  };
  self.lhtml = _.flatten( [params.lhtml] );
};

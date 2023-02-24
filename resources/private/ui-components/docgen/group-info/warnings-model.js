// List of warnings (validation errors).
// More like a convenience template than a full-blown component. See
// components docgen-group-info, huoneistot-table, docgen-table and
// docgen-group componets for usage.
// Params:
//
//  warnings: Observable array (not necessarily ko.observableArray,
//  though), where each item MUST have the following properties:
//
//      id: Unique id for the list item. This can be referred via
//      aria-errormessage from the client code.
//      label: Text string (NOT a l10n term) that denotes the source field.
//      error: L10n term for the error/warning.
//
// When warnings array is empty, nothing is shown.
LUPAPISTE.WarningsModel = function( params ) {
  "use strict";
  var self = this;

  self.warnings = params.warnings;

  self.ariaLabel = function( warning ) {
    return ko.unwrap( warning.label )
      + ": " + loc( ko.unwrap( warning.error ));
  };
};

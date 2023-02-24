// Form cell is a generic wrapper for different (cell) components that
// are laid out in the form-grid. The idea is that the labeling, value
// validation and mandatory status (required) are taken care of in this
// level thus making the actual cell-components very lightweight.
// params [optional]:
//
//  [message]: Observable that includes a message ltext that is shown below
//  the cell.
//  [warning]: Observable that includes the error ltext if the cell is
//  invalid. Warning always overrides message.
//  [required]: Is the cell required to have value. Can be observable
//  (default false).
//  label: Label ltext for the cell
//  cell:  Cell componen name without the cell prefix (e.g, 'date', 'text')
//  attr:  Params object that will be passed to the cell components attr binding.
//
// All the other parameters (and attr) are passed as parameters to the cell component.
LUPAPISTE.FormCellModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.warning = params.warning;
  self.message = params.message;

  self.required = params.required
    ? self.disposedPureComputed( function() {
      return !_.trim(params.value());
    })
  : false;

  self.showMessage = self.disposedComputed( function() {
    return !ko.unwrap( self.warning ) && ko.unwrap( self.message );
  });

  self.spanTestId = function( span ) {
    return params.testId ? sprintf( "%s-%s", params.testId, span ) : null;
  };


  self.isMandatory = Boolean( self.required );
  self.id = _.uniqueId( "form-cell-id-");
  self.warningId = self.id + "-warning";
  self.label = params.label;
  self.componentName = "cell-" + params.cell;
  self.componentParams = _.omit( params, ["$raw", "message",
                                          "id", "label", "cell"]);
  self.componentParams.attr = _.defaults( self.componentParams.attr,
                                          {id: self.id,
                                           "aria-errormessage": self.warningId});
};

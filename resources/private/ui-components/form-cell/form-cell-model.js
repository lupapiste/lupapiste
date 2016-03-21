// Form cell is a generic wrapper for different (cell) components that
// are laid out in the form-grid. The idea is that the labeling, value
// validation and mandatory status (required) are taken care of in this
// level thus making the actual cell-components very lightweight.
// params [optional]:
//  [warning]: Observable that includes the error ltext if the cell is invalid.
//  [required]: Is the cell required to have value (default false)
//  label: Label ltext for the cell
//  cell:  Cell componen name without the cell prefix (e.g, 'date', 'text')
//  attr:  Params object that will be passed to the cell components attr binding.
//
// All the other parameters (and attr) are passed as parameters to the cell component.
LUPAPISTE.FormCellModel = function( params ) {
  "use strict";
  var self = this;

  self.warning = params.warning;

  self.required = params.required
    ? ko.pureComputed( function() {
      return !_.trim(params.value());
    })
  : false;

  self.isMandatory = Boolean( self.required );
  self.id = _.uniqueId( "form-cell-id-");
  self.label = params.label;
  self.componentName = "cell-" + params.cell;
  self.componentParams = _.omit( params, ["$raw", "warning", "required", "id", "label", "cell"]);
  self.componentParams.attr = _.defaults( self.componentParams.attr, {id: self.id});
};

// Simple editor component for text - value pairs.
// Params [optional]

// entries: observableArray, where each item is has both text and
// value properties. Value can be plain or observable.
// textTitle: L10n key for the text heading
// valueTitle: L10n key for the text heading
// [textProperty]: Name of the text property (default: 'text')
// [valueProperty]: Name of the value property (default: 'value')
// [addTitle]: L10n key for add button (default results in Add row).
// [testId]: Prefix for testIds (e.g, testId-text-0, testId-value-1,
// testId-remove-3 testId-add). Default is dictionary.
// [fixed]: If true, the text column is immutable. (default: false)

// From EnableComponentModel:[enable] and [disable]. When disabled,
// buttons are not shown.
LUPAPISTE.DictionaryModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.EnableComponentModel( params ));

  self.textTitle = params.textTitle;
  self.valueTitle = params.valueTitle;
  self.addRowTitle = params.addTitle || "muutHankkeet.addHanke";
  self.rows = params.entries;
  self.textField = params.textProperty || "text";
  self.valueField = params.valueProperty || "value";
  self.isFixed = params.fixed;

  self.testId = function( text, index) {
    var tid = sprintf( "%s-%s", params.testId || "dictionary", text );
    return _.isNumber( index ) ? sprintf( "%s-%s", tid, index ) : tid;
  };

  self.removeRow = function( data ) {
    self.rows.remove( data );
  };

  self.addRow = function() {
    self.rows.push({});
  };

  self.textColumnCss = function() {
    return {"tabby--40": !self.isFixed};
  };

  self.valueColumnCss = function() {
    return {"tabby--40": !self.isFixed,
            "tabby--100": self.isFixed};
  };
};

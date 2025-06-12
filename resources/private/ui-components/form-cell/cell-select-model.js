// Cell support for select element.
// Params [optional]:
//  value: Initial value.
//  options: KO select options
//  [optionsText]: KO select optionsText
//  [optionsValue]: KO select optionsValue
LUPAPISTE.CellSelectModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.CellModel( params ) );

  self.optionsText = _.isFunction( params.optionsText)
                   ? params.optionsText
                   : function( item ) {
                     var opt = params.optionsText;
                     return loc( opt ? item[opt] : item);
                   };

  self.optionsValue = _.isFunction( params.optionsValue )
                    ? params.optionsValue
                    : function( item ) {
                      var opt = params.optionsValue;
                      return opt ? item[opt] : item;
                    };
};

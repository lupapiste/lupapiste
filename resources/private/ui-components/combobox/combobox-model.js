// Combobx is a textinput with a list of predefined values.
// Parameters [optional]:
//   value: textinput value observable.
//   list: predefined value list. Can be either array or observable
//   array.
//   [testId: Text input test id (combobox-input)]
LUPAPISTE.ComboboxModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.value = params.value;
  self.list  = params.list;
  self.selectedIndex = ko.observable(-1);
  self.testId = params.testId || "combobox-input";

  // Textinput focus
  self.hasFocus = ko.observable();
  // Linger is true if the user can still interact with the list even
  // if the textinput no longer has focus.
  var linger = ko.observable();

  self.shortList = self.disposedPureComputed( function() {
    self.selectedIndex( -1 );
    var result = _.filter( ko.unwrap( self.list ),
                           function( item ) {
                             return _.includes( _.toLower(item),
                                                _.toLower(_.trim( self.value())));
                           });
    // List is not shown if it has only one item and it is exact match.
    return _.size( result ) && _.first( result )  === _.trim(self.value())
      ? []
      : result;
  });

  self.isDisabled = self.disposedPureComputed( function() {
    var disable = ko.unwrap( params.disable );
    var enable = ko.unwrap( params.enable );

    return (_.isBoolean( disable ) && disable)
      || (_.isBoolean( enable ) && !enable );
  });


  self.hover = function( data ) {
    self.selectedIndex( _.indexOf( self.shortList(), data ));
    linger( true );
  };


  self.isSelected = function( index  ) {
    return index() === self.selectedIndex();
  };

  self.keyDown = function( data, event ) {
    var index = self.selectedIndex();
    switch( event.keyCode ) {
    case 13: // Enter
      if( index >= 0 ) {
        self.value( self.shortList()[index]);
      }
      self.selectedIndex( -1 );
      break;
    case 38: // Arrow up
    case 40: // Arrow down
      var size = _.size( self.shortList());
      var newIndex = 0;
      var lastIndex = size ? size - 1 : 0;
      if( size ) {
        if( index < 0 ) {
          newIndex = event.keyCode === 38 ? lastIndex : 0;
        } else {
          newIndex = (index + (event.keyCode === 38 ? lastIndex : 1)) % size;
        }
        self.selectedIndex( newIndex );
      }
      break;
    default:
      // Passing key onwards
      return true;
    }
  };



  self.doNotLinger = _.wrap( false, linger );

  self.showList = self.disposedPureComputed( function() {
    return (linger() || self.hasFocus()) && _.size( self.shortList());
  });

  self.select = function( data ) {
    self.value( data );
  };
};

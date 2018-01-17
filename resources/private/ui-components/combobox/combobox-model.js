// Combobx is a textinput with a list of predefined values.
// Parameters [optional]:
//   value: Value observable. Note that the observable updates when
//   combobox loses focus. However, changes to the observable are
//   immediately reflected in the combobox.
//   list: predefined value list. Can be either array or observable
//   array.
//   [testId]: Text input test id (combobox-input)
//   Combobox editable state is calculated from enable and disable
//   params (default editable). See EnableComponentModel for details.
LUPAPISTE.ComboboxModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.EnableComponentModel( params ));

  var outsideValue = params.value;
  self.list  = params.list;
  self.textInput = ko.observable( outsideValue() );
  self.selectedIndex = ko.observable(-1);
  self.testId = params.testId || "combobox-input";

  var localStorageKey = "combobox-prev-entries-for-" + params.prevEntriesKey;

  // Textinput focus
  self.hasFocus = ko.observable();

  var hadFocus = false;
  self.disposedSubscribe( self.hasFocus, function( flag ) {
    if( hadFocus && !flag ) {
      outsideValue( self.textInput());
    }
    hadFocus = flag;
  });

  self.disposedSubscribe( outsideValue, function( value ) {
    self.textInput( value );
  });

  function updateAfterDelay( text ) {
    // No changes during delay
    if( text === self.textInput() ) {
      outsideValue( self.textInput());
    }
  }

  // Mostly for robots' sake, we update outsideValue after delay even
  // if the combobox still has focus.
  self.disposedSubscribe( self.textInput, function( value ) {
    _.delay( updateAfterDelay, 1000, value );
  });

  // Linger is true if the user can still interact with the list even
  // if the textinput no longer has focus.
  var linger = ko.observable();

  self.shortList = self.disposedPureComputed( function() {
    self.selectedIndex( -1 );
    var items = ko.unwrap(self.list);
    // If there are no options, we offer the values that user has previously input for this field
    if ((!items || items.length === 0) && params.prevEntriesKey && window.localStorage.getItem(localStorageKey)) {
      items = JSON.parse(window.localStorage.getItem(localStorageKey));
    }
    var result = _.filter( items,
                           function( item ) {
                             return _.includes( _.toLower(item),
                                                _.toLower(_.trim( self.textInput())));
                           });
    // List is not shown if it has only one item and it is exact match.
    return _.size( result ) && _.first( result )  === _.trim(self.textInput())
      ? []
      : result;
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
        outsideValue( self.shortList()[index]);
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
    outsideValue( data );
  };
};

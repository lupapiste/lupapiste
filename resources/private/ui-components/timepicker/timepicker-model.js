// Simple timepicker
// Parameters [optional]:
// value: Value observable (string)
// [enable]: see `EnableComponentModel`
// [disable]: see `EnableComponentModel`
// [testId]: Test identifier for the input
LUPAPISTE.TimepickerModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.EnableComponentModel( params ));

  self.value = params.value;
  self.testId = params.testId;
  self.hasFocus = ko.observable();
  self.showPicker = ko.observable( false );

  var currentHour = ko.observable();
  var currentMinute = ko.observable();

  function closePicker() {
    self.showPicker( false );
  }

  function setTimePart( isHour, value ) {
    if( isHour ) {
      currentHour( value );
    } else {
      currentMinute( value );
    }
    if( _.isNumber( currentHour()) && _.isNumber( currentMinute() )) {
      var newValue = sprintf( "%d:%02d", currentHour(), currentMinute());
      if( newValue !== self.value() ) {
        self.value( newValue );
      }
    }
    // Clicking minutes closes picker
    self.showPicker( isHour );
  }

  function capInt( s, cap ) {
    var n = parseInt( s );
    return n < cap ? n : null;
  }

  function valueToParts( value ) {
    var parts = /(\d+):(\d+)/.exec( _.trim( value ));
    if( parts ) {
      currentHour( capInt( parts[1], 24 ));
      currentMinute( capInt( parts[2], 60 ));
    } else {
      currentHour( null );
      currentMinute( null );
    }
  }

  self.disposedSubscribe( self.value, valueToParts );

  valueToParts( self.value() );

  self.disposedSubscribe( self.hasFocus, function( flag ) {
    if( flag ) {
      self.showPicker( true );
    }
  });

  function isSelected( isHour, value ) {
    return value === (isHour ? currentHour() : currentMinute());
  }

  function cell( isHour, value ) {
    var isGood = _.isNumber( value );
    var cls = isHour ? "timepicker--hour" : "timepicker--minute";
    var txt = isGood ? sprintf( "%02d", value ) : "";
    if( isGood && !isHour ) {
      txt = ":" + txt;
    }
    return {css: _.set( {"timepicker--selected": isGood && isSelected( isHour, value ),
                         "timepicker--selectable": isGood},
                        cls,
                        true),
            text: txt || "",
            click: isGood ? _.partial( setTimePart, isHour, value) : _.noop};
  }

  self.rows = self.disposedComputed(function() {
    return _.map( [{hours: _.range( 5), minute: 0},
                      {hours: _.range( 5, 10), minute: 15},
                      {hours: _.range( 10, 15), minute: 30},
                      {hours: _.range( 15, 20), minute: 45},
                      {hours: _.concat(_.range( 20, 24), null)}],
                     function( row ) {
                       return {cells: _.concat( _.map( row.hours,
                                                       function( h ) {
                                                         return cell( true, h);
                                                       }),
                                                [cell( false, row.minute)])};
                     });
  });

  // Body event handlers that close the picker

  $("body").click( closePicker );
  self.addHubListener( "side-panel-esc-pressed", closePicker );

  self.addToDisposeQueue( {dispose: function() {
    $("body").off( "click", null, closePicker );
  }});

};

// Simple timepicker
// Parameters [optional]:
// value: Value observable (string)
// [enable]: see `EnableComponentModel`
// [disable]: see `EnableComponentModel`
// [testId]: Test identifier for the input
// [placeholder]: Placeholder ltext (default empty)
// [ariaLabel]: aria-label ltext (default null)
// [ariaErrorMessage]: aria-errormessage attribute value. Can be observable.
// [id]: Id attribute
// [required]. Whether the field is required. Can be observable (default false)
//
// The time format is h:mm. Invalid times are highlighted but still
// passed forward. Thus, the client code is ultimately responsible for
// the validation.
LUPAPISTE.TimepickerModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.EnableComponentModel( params ));

  self.value = params.value;
  self.testId = params.testId;
  self.placeholder = params.placeholder;

  self.hasFocus = ko.observable();
  self.showError = ko.observable( false );
  self.showPicker = ko.observable( false );
  self.ariaErrorMessage = params.ariaErrorMessage;
  self.id = params.id;
  self.required = params.required;
  self.ariaLabel = params.ariaLabel;

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
    if( _.isNumber( currentHour())) {
      var newValue = sprintf( "%d:%02d",
                              currentHour(),
                              _.isNumber( currentMinute() )
                              ? currentMinute() : 0);
      if( newValue !== self.value() ) {
        self.value( newValue );
      }
    }
    // Clicking minutes closes picker
    self.showPicker( isHour );
  }

  function capInt( obs, s, cap ) {
    var n = parseInt( s );
    obs( n < cap ? n : null );
    return n < cap;
  }

  function resetCurrent( error ) {
    currentHour( null );
    currentMinute( null );
    self.showError( error );
  }

  function valueToParts( value ) {
    var v = _.trim( value );
    if( v === "" ) {
      resetCurrent( false );
      return;
    }
    var parts = /^(\d+)(:(\d+))?$/.exec( v );
    switch( _.size( _.filter( parts, _.identity ))) {
    case 0: // Bad input
      resetCurrent( true );
      break;
    case 4: // Fully formed
      var h = capInt( currentHour, parts[1], 24 );
      var m = capInt( currentMinute, parts[3], 60 );
      self.showError( !(h && m));
      break;
    case 2: // Only hours
      h = capInt( currentHour, parts[1], 24 );
      if( h ) {
        setTimePart( false, 0 );
        self.showError( false );
        break;
      } else {
        resetCurrent( true );
      }
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

  $("body").on("click", closePicker );
  self.addHubListener( "side-panel-esc-pressed", closePicker );

  self.addToDisposeQueue( {dispose: function() {
    $("body").off( "click", null, closePicker );
  }});

};

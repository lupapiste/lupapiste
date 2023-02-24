// Yet another data input component. Abstracts an individual input element.
// Params [optional]:
//
//  callback: Function that receives a following result object, when
//  the input value has changed:
//     isValid: Whether the value is a valid date.
//
//     text: Value as text. For valid dates this is either blank
//     string or datestring in the current language format.
//
//     moment: Value as moment. Null for invalid and blank dates.
//
//  [value]: Initial value that can be timestamp (integer), datestring
//  (in the current language format) or moment. Invalid (and blank)
//  strings result in empty value. Value can be observable but there
//  is no guaranteed two-way binding. In other words, the date changes
//  are only reliably passed back via the callback function. However,
//  if the value is observable, its changes are reflected in the dateinput.
//
//  [enable/disable] Like in KO, see EnableComponentModel for details.
//  [text/ltext]: For aria-label.
//  [id] Id attribute. Typically used for label's for attribute.
//  [testId] Test-id, if not given falls back to id.
//  [required]: Whether the input is mandatory. Can be observable.
//
//  [css]: Additional classes for input. Value can be either KO css
//  object, array of classes or class string where individual classes
//  are separated by whitespace.
//
//  [validator]: Function that receives moment as argument and return
//  value is interpreter as validity boolean (true is valid).
LUPAPISTE.DateInputModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.EnableComponentModel( params ));

  function momentify( v ) {
    if( _.isNumber( v )) {
      return util.toMoment( v );
    }
    if( _.isString( v ) ) {
      return util.toMoment( v, loc.getCurrentLanguage() );
    }
    if( moment.isMoment( v )) {
      return v;
    }
  }

  self.value = ko.observable();
  self.datepicker = ko.observable();

  function initValue( v ) {
    var m = momentify( ko.unwrap( v ) );
    self.value( m && util.formatMoment( m ));
    self.datepicker( m && new Date( m.valueOf() ));
  }

  initValue( ko.unwrap(params.value) );

  if( ko.isObservable( params.value )) {
    self.disposedSubscribe( params.value, initValue );
  }

  self.required = params.required;
  self.text = params.ltext ? loc( params.ltext ) : params.text;
  var callbackFn = params.callback;
  var validatorFn = params.validator || _.stubTrue;

  self.css = self.disposedPureComputed( function() {
    var p = ko.unwrap( params.css );

    if( _.isString( p )) {
      p = _.split( p, /\s+/);
    }
    if( _.isArray( p )) {
      return _.reduce( p,
                       function( acc, cls ) {
                         return _.set( acc, cls, true );
                       },
                       {});
    }
    return _.isObject( p ) ? p : null;
  });

  self.id = params.id || _.uniqueId( "date-input" );
  self.testId = params.testId || self.id;

  function isValid( v ) {
    var m = momentify( v );
    return _.isBlank( v ) || (m && validatorFn( m ));
  }

  self.isInvalid = self.disposedComputed( function() {
    return !isValid( self.value() );
  });

  self.disposedSubscribe( self.value, function( v ) {
    callbackFn( {isValid: isValid( v ),
                 text: v,
                 moment: momentify( v )});

  });
};

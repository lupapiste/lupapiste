LUPAPISTE.CalendarSlotBubbleModel = function( params ) {
  "use strict";
  var self = this;

  self.bubbleVisible = ko.observable(params.bubbleVisible);

  self.waiting = params.waiting;
  self.error = params.error;

  self.send = function() {};

  self.init = function() {
    self.error( false );
/*    ajax.query( "companies")
    .pending( self.waiting)
    .success( function( res ) {
      companies( _.map( res.companies, companyEntry ));
    })
    .error( function( res ) {
      self.error( res.text);
    } )
    .complete( function() {
      self.waiting( false );
    })
    .call(); */
  };
};
LUPAPISTE.CardModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.cardService;
  var deck = params.deck;
  var card = params.card;

  if( params.selected ) {
    service.currentCard( deck )( card );
  }

  self.showCard = self.disposedComputed( function() {
    return service.currentCard( deck )() === card;
  });

  
  
};


LUPAPISTE.CardService = function() {
  "use strict";
  var self = this;

  // Deck name: selected card observable.
  var decks = {};

  self.currentCard = function( deck ) {
    if( !_.has( decks, deck ) ) {
      _.set( decks, deck, ko.observable());
    }
    return _.get( decks, deck );
  };

  self.showCard = function ( deck, card, setDefault ) {
    var current = self.currentCard( deck );
    if( !current() && setDefault ) {
      current( card );
    }
    return current() === card;
  };
  
  hub.subscribe( "cardService::select", function( event ) {
    self.currentCard( event.deck)(event.card) ;    
  });
};

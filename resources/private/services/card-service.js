// Simple mechanism for UI view changes.
// A card denotes visible view. Deck is a group of selectable cards.
LUPAPISTE.CardService = function() {
  "use strict";
  var self = this;

  // Deck name: selected card observable.
  var decks = {};

  // Returns the current card observable for the given deck.
  self.currentCard = function( deck ) {
    if( !_.has( decks, deck ) ) {
      _.set( decks, deck, ko.observable());
    }
    return _.get( decks, deck );
  };

  // Returns true if the given card should be shown.
  //   deck: deck name (string)
  //   card: string
  //   setDefault: if truthy then the card is selected if there is no
  //   current card.
  self.showCard = function ( deck, card, setDefault ) {
    var current = self.currentCard( deck );
    if( !current() && setDefault ) {
      current( card );
    }
    return current() === card;
  };

  function resetDecks() {
  _.each( _.values( decks ),
            function( obs ) {
              obs(undefined);
            });
  }

  hub.subscribe( "cardService::select", function( event ) {
    self.currentCard( event.deck)(event.card) ;    
  });

  hub.subscribe( "contextService::leave", resetDecks);
  hub.subscribe( "contextService::enter", resetDecks);
};

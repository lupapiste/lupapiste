// Somewhat dynamic card button group.
//
// The group can consist arbitrary number of buttons, some of which
// might not be initially visible. In that case an additional Show
// more / Show less button is shown.
//
// Parameters [optional]:
//
//  cards: List of card-button parameters. See `card-button-model.js`
//        for details. However, the component assumes that every given
//        defintion results in a visible button.
//
//  [threshold]: How many cards are initially shown. If not positive,
//        every card is always shown. Default is zero.
//
//  [lessLtext/moreLtext]: More/less button labels. Defaults are
//       "a11y.show-more" and "a11y.show-less" respectively.
LUPAPISTE.CardButtonsModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var threshold = params.threshold || 0;

  self.showAll = ko.observable( false );

  self.visibleCards = self.disposedPureComputed( function() {
    var cards = ko.unwrap( params.cards );
    return  self.showAll() || threshold <= 0 || _.size( cards ) <= threshold
      ? cards
      : _.take( cards, threshold );
  });

  // If the result is falsey, the button is not shown.
  self.toggleLtext = self.disposedPureComputed( function() {
    var cards = ko.unwrap( params.cards );
    if( threshold > 0 && _.size( cards ) > threshold ) {
      return self.showAll()
        ? _.get( params, "lessLtext", "a11y.show-less")
        : _.get( params, "moreLtext", "a11y.show-more");
    }
  });

  self.lessMore = function() {
    self.showAll( !self.showAll());
  };
};

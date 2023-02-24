// Simple component for page display, next and previous buttons.
// Parameters [optional]:
//  pageCount: Total page count observable.
//  currentPage: Current page observable. The first page is zero (shown as one).
//  [testId]: Test id postfix. For example, if testId is 1, the final
//  testIds are `page-flip-previous-1`, `page-flip-nex-1` and
//  `page-flip-text-1`.
LUPAPISTE.PageFlipModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.pageCount = params.pageCount;
  self.currentPage = params.currentPage;
  var postfix = params.testId ? "-" + params.testId : "";

  self.testId = function( item ) {
    return "page-flip-" + item + postfix;
  };

  self.canPreviousPage = self.disposedPureComputed( function() {
      return self.currentPage() !== 0;
  });

  self.previousPage = function() {
    self.currentPage( self.currentPage() - 1 );
  };

  self.nextPage = function() {
    self.currentPage( self.currentPage() + 1 );
  };

  self.canNextPage = self.disposedPureComputed( function() {
    return self.currentPage() + 1 < self.pageCount();
  });

  self.pageText = self.disposedPureComputed( function() {
    return sprintf( "%s/%s", self.currentPage() + 1, self.pageCount());
  });

};

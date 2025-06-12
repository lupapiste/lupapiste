// Operation tree component for various purposes
// Parameters [optional]:
//
// operationTree: operations2tree structure (see `create.js` for details)
//
// lastPageComponent: The name of the component to be displayed when
// the operation has been selected.
// lastPageParams: Parameters for the last page component.
//
// [selected]: Observable, where the selection is stored. At any given
// time it contains the current "leaf" and at the end the actual
// operation.
//
// [cancelFn]: Click function for the Cancel button. If not given, the button is not displayed.
//
// [error]: Observable that can contain error message. If so, the message is shown instead of the tree.
LUPAPISTE.OperationTreeModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var opTree = params.operationTree;
  var path = ko.observableArray();
  self.selected = params.selected || ko.observable();
  self.lastPage = params.lastPageComponent;
  self.lastPageParams = params.lastPageParams;
  self.cancelFn = params.cancelFn;
  self.error = params.error || ko.observable();

  self.ready = opTree;

  self.disposedComputed( function() {
    if( !opTree() ) {
      self.selected( null );
      path.removeAll();
    }
  });

  self.operationText = function( op ) {
    return op ? loc( "operations.tree." + op ) : "";
  };

  self.treeTitle = self.disposedPureComputed( function() {
    return _.size( path() )
      ? _( path() )
      .map( function( op ) {
        return self.operationText( op );
      })
      .join( " / ")
    : loc( "create.choose-op");
  });

  function findBranch( pairs, path ) {
    if( _.isEmpty( path )) {
      return pairs;
    }
    var pair = _.find( pairs, function( p ) {
      return _.get( p, "0.op") === _.first( path );
    });
    return findBranch( _.last( pair ), _.tail( path ));
  }

  self.items = self.disposedPureComputed( function() {
    var branch = findBranch( opTree(), path());
    return _.isArray( branch )
        ? _.map( branch,
                 function( xs ) {
                   return _.get( xs, "0.op" );
                 })
      : null;
  });

  self.select = function( op ) {
    path.push( op );
    var branch = findBranch( opTree(), path());
    self.selected( _.isObject( branch ) ? _.get( branch, "op") : op );
  };

  self.hasPath = self.disposedPureComputed( function() {
    return _.size( path() ) && !self.error();
  });

  self.back = function() {
    path.pop();
  };

  self.start = function() {
    path.removeAll();
  };
};

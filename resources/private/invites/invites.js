/*
	invites.js:
*/

var invites = function() {
	
	function getInvites(callback) {
		debug("getting invites");
		ajax.query("invites")
			.success(function(d) {
				debug("got some nice invites");
				if (callback) callback(d);
			})
			.call();
	}

	return {
		getInvites : getInvites
	};
	
}();

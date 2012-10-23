/*
	invites.js:
*/

var invites = function() {
	
	function getInvites(callback) {
		debug("getting invites");
		ajax.query("invites")
			.success(function(d) {
				if (callback) callback(d);
			})
			.call();
	}

	return {
		getInvites : getInvites
	};
	
}();

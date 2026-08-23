"""The pairwise scorer: score(player, table) -> value.

Trained as an afterstate value function (see rl/train.py) -- the score of
the (player, table) pair a player actually ends up seated at is trained to
predict discounted future *global* casino reward, not a reward attributed to
that one player. This is what lets the network learn externalities (e.g. an
Obnoxious player dragging down a table of Grinders) even though no single
player's row carries an individually attributed reward.
"""
import torch
from torch import nn

from .features import PLAYER_FEATURE_DIM, TABLE_FEATURE_DIM


class PairwiseScorer(nn.Module):
    def __init__(self, hidden_dims=(128, 64)):
        super().__init__()
        input_dim = PLAYER_FEATURE_DIM + TABLE_FEATURE_DIM
        layers = []
        previous = input_dim
        for hidden in hidden_dims:
            layers.append(nn.Linear(previous, hidden))
            layers.append(nn.ReLU())
            previous = hidden
        layers.append(nn.Linear(previous, 1))
        self.network = nn.Sequential(*layers)

    def forward(self, player_feat: torch.Tensor, table_feat: torch.Tensor) -> torch.Tensor:
        x = torch.cat([player_feat, table_feat], dim=-1)
        return self.network(x).squeeze(-1)
